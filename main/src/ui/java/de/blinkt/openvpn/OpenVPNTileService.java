/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package ru.oig.etyvpn;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import ru.oig.etyvpn.core.ConnectionStatus;
import ru.oig.etyvpn.core.IOpenVPNServiceInternal;
import ru.oig.etyvpn.core.OpenVPNService;
import ru.oig.etyvpn.core.ProfileManager;
import ru.oig.etyvpn.core.VPNLaunchHelper;
import ru.oig.etyvpn.core.VpnStatus;


/**
 * Created by arne on 22.04.16.
 */
@TargetApi(Build.VERSION_CODES.N)
public class OpenVPNTileService extends TileService implements VpnStatus.StateListener {

    @SuppressLint("Override")
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onClick() {
        super.onClick();
        final VpnProfile bootProfile = getQSVPN();
        if (bootProfile == null) {
            Toast.makeText(this, R.string.novpn_selected, Toast.LENGTH_SHORT).show();
        } else {
            if (!isLocked())
                clickAction(bootProfile);
            else
                unlockAndRun(new Runnable() {
                    @Override
                    public void run() {
                        clickAction(bootProfile);
                    }
                });
        }
    }

    private void clickAction(VpnProfile bootProfile) {
        if (VpnStatus.isVPNActive()) {
            Intent intent = new Intent(this, OpenVPNService.class);
            intent.setAction(OpenVPNService.START_SERVICE);
            bindService(intent, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder binder) {
                    IOpenVPNServiceInternal service = IOpenVPNServiceInternal.Stub.asInterface(binder);

                    if (service != null)
                        try {
                            service.stopVPN(false);
                        } catch (RemoteException e) {
                            VpnStatus.logException(e);
                        }

                    unbindService(this);
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {

                }
            }, Context.BIND_AUTO_CREATE);
        } else
            launchVPN(bootProfile, this);
    }


    @SuppressLint("Override")
    @TargetApi(Build.VERSION_CODES.N)
    void launchVPN(VpnProfile profile, Context context) {
          VPNLaunchHelper.startOpenVpn(profile, getBaseContext(), "QuickTile", true);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onTileAdded() {
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        VpnStatus.addStateListener(this);
    }


    @TargetApi(Build.VERSION_CODES.N)
    public VpnProfile getQSVPN() {
        return ProfileManager.getAlwaysOnVPN(this);
    }

    @Override
    public void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level, Intent intent) {
        VpnProfile vpn;
        Tile t = getQsTile();
        if (level == ConnectionStatus.LEVEL_AUTH_FAILED || level == ConnectionStatus.LEVEL_NOTCONNECTED) {
            // No VPN connected, use standard VPN
            vpn = getQSVPN();
            if (vpn == null) {
                t.setLabel(getString(R.string.novpn_selected));
                t.setState(Tile.STATE_UNAVAILABLE);
            } else {
                t.setLabel(getString(R.string.qs_connect, vpn.getName()));
                t.setState(Tile.STATE_INACTIVE);
            }
        } else {
            vpn = ProfileManager.get(getBaseContext(), VpnStatus.getLastConnectedVPNProfile());
            String name;
            if (vpn == null)
                name = "null?!";
            else
                name = vpn.getName();
            t.setLabel(getString(R.string.qs_disconnect, name));
            t.setState(Tile.STATE_ACTIVE);
        }

        t.updateTile();
    }

    @Override
    public void setConnectedVPN(String uuid) {

    }

    @Override
    public void onStopListening() {
        VpnStatus.removeStateListener(this);
        super.onStopListening();
    }
}
