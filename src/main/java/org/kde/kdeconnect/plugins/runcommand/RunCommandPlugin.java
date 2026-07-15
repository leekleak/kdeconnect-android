/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.runcommand;

import static org.kde.kdeconnect.plugins.runcommand.RunCommandWidgetProviderKt.forceRefreshWidgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.compose.runtime.MutableState;
import androidx.compose.runtime.snapshots.SnapshotStateList;
import androidx.preference.PreferenceManager;

import org.apache.commons.collections4.iterators.IteratorIterable;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.plugins.Plugin;
import org.kde.kdeconnect.plugins.PluginInfo;
import org.kde.kdeconnect.ui.navigation.Navigator;
import org.kde.kdeconnect.ui.navigation.RunCommandKey;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class RunCommandPlugin extends Plugin {

    private final static String PACKET_TYPE_RUNCOMMAND = "kdeconnect.runcommand";
    private final static String PACKET_TYPE_RUNCOMMAND_OUTPUT = "kdeconnect.runcommand.output";
    private final static String PACKET_TYPE_RUNCOMMAND_REQUEST = "kdeconnect.runcommand.request";
    public final static String KEY_COMMANDS_PREFERENCE = "commands_preference_";

    private final ArrayList<JSONObject> commandList = new ArrayList<>();
    private final ArrayList<CommandsChangedCallback> callbacks = new ArrayList<>();
    private final ArrayList<CommandEntry> commandItems = new ArrayList<>();
    private final SnapshotStateList<RunCommandOutput> output = new SnapshotStateList<>();

    private SharedPreferences sharedPreferences;
    private boolean canAddCommand;

    public RunCommandPlugin(Context context, Device device) {
        super(context, device);
    }

    @NonNull
    @Override
    public PluginInfo getPluginInfo() {
        return RunCommandPluginInfo.INSTANCE;
    }

    public void addCommandsUpdatedCallback(CommandsChangedCallback newCallback) {
        callbacks.add(newCallback);
    }

    public void removeCommandsUpdatedCallback(CommandsChangedCallback theCallback) {
        callbacks.remove(theCallback);
    }

    interface CommandsChangedCallback {
        void update();
    }

    public MutableState<Boolean> commandRunning = new MutableState<>() {
        private boolean value = false;

        @Override
        public Boolean getValue() {
            return value;
        }

        @Override
        public void setValue(Boolean aBoolean) {
            value = aBoolean;
        }

        // You need to override these, but they are not being used
        @Override
        public Boolean component1() {
            return null;
        }

        @NonNull
        @Override
        public Function1<Boolean, Unit> component2() {
            return null;
        }
    };

    public SnapshotStateList<RunCommandOutput> getOutput() {
        return output;
    }

    public ArrayList<JSONObject> getCommandList() {
        return commandList;
    }

    public ArrayList<CommandEntry> getCommandItems() {
        return commandItems;
    }

    @Override
    public @NotNull List<@NotNull PluginUiButton> getUiButtons() {
        return List.of(new PluginUiButton(context.getString(R.string.pref_plugin_runcommand), R.drawable.run_command_plugin_icon_24dp, parentActivity -> {
            Navigator navigator = ((org.kde.kdeconnect.ui.MainActivity) parentActivity).getScope().get(kotlin.jvm.JvmClassMappingKt.getKotlinClass(Navigator.class), null, null);
            navigator.goTo(new RunCommandKey(device.getDeviceId()));
            return Unit.INSTANCE;
        }));
    }

    @Override
    public boolean onCreate() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        requestCommandList();
        return true;
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {
        if (np.has("commandList")) {
            commandList.clear();
            try {
                commandItems.clear();
                JSONObject obj = new JSONObject(np.getString("commandList"));
                for (String s : new IteratorIterable<>(obj.keys())) {
                    JSONObject o = obj.getJSONObject(s);
                    o.put("key", s);
                    commandList.add(o);

                    try {
                        commandItems.add(
                                new CommandEntry(o)
                        );
                    } catch (JSONException e) {
                        Log.e("RunCommand", "Error parsing JSON", e);
                    }
                }

                Collections.sort(commandItems, Comparator.comparing(CommandEntry::getName));

                // Used only by RunCommandControlsProviderService to display controls correctly even when device is not available
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    JSONArray array = new JSONArray();

                    for (JSONObject command : commandList) {
                        array.put(command);
                    }

                    sharedPreferences.edit()
                            .putString(KEY_COMMANDS_PREFERENCE + device.getDeviceId(), array.toString())
                            .apply();
                }

                forceRefreshWidgets(context);

            } catch (JSONException e) {
                Log.e("RunCommand", "Error parsing JSON", e);
            }

            for (CommandsChangedCallback aCallback : callbacks) {
                aCallback.update();
            }

            device.onPluginsChanged();

            canAddCommand = np.getBoolean("canAddCommand", false);

            return true;
        } else if (np.has("stdout")) {
            List<String> stdOut = np.getStringList("stdout");
            List<String> stdErr = np.getStringList("stderr");
            assert stdOut != null;
            assert stdErr != null;
            for (String line : stdOut) {
                Log.d("STDOUT", "Line:" + line);
                output.add(new RunCommandOutput(line, false));
            }
            for (String line : stdErr) {
                Log.d("STDERR", "Line:" + line);
                output.add(new RunCommandOutput(line, false));
            }

            return true;
        } else if (np.has("commandFinished")) {
            commandRunning.setValue(false);

            RunCommandOutput newCommand = new RunCommandOutput(">", true);
            if (Objects.equals(output.get(output.size() - 1), newCommand)) {
                return true;
            }

            output.removeAll(output.stream().filter(output -> output.getString().equals(">")).collect(Collectors.toList()));

            output.add(newCommand);

            return true;
        }
        return false;
    }

    public void runCommand(String cmdKey) {
        Log.d("RunCommand", "Sending " + cmdKey);
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("key", cmdKey);
        device.sendPacket(np);
        commandRunning.setValue(true);
    }

    private void requestCommandList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("requestCommandList", true);
        device.sendPacket(np);
    }

    public boolean canAddCommand() {
        return canAddCommand;
    }

    void sendSetupPacket() {
        NetworkPacket np = new NetworkPacket(RunCommandPlugin.PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("setup", true);
        device.sendPacket(np);
    }

    void sendStop() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("stop", true);
        device.sendPacket(np);
    }
}
