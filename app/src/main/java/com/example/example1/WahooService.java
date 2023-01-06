package com.example.example1;

import android.app.Service;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;


import com.wahoofitness.common.datatypes.Temperature;
import com.wahoofitness.connector.HardwareConnector;
import com.wahoofitness.connector.HardwareConnectorEnums;
import com.wahoofitness.connector.capabilities.Capability;
import com.wahoofitness.connector.capabilities.Heartrate;
import com.wahoofitness.connector.capabilities.TemperatureCapability;
import com.wahoofitness.connector.conn.connections.SensorConnection;
import com.wahoofitness.connector.conn.connections.params.ConnectionParams;
import com.wahoofitness.connector.listeners.discovery.DiscoveryListener;

import java.util.ArrayList;
import java.util.List;

public class WahooService extends Service implements DiscoveryListener, SensorConnection.Listener, Heartrate.Listener{
    private HardwareConnector mHardwareConnector;
    private SensorConnection mSensorConnection;
    private final HardwareConnector.Listener mHardwareConnectorListener = new WahooListener();
    private static final String TAG = "WahooService";
    private List<WahooServiceListener> listenerList;

    public WahooService (){
        listenerList = new ArrayList<WahooServiceListener>();
    }

    private static WahooService instance_;

    public void addListener( WahooServiceListener Listener){
        listenerList.add(Listener);
        Log.i(TAG, "AddListener: "+Listener+ " " + listenerList.size());

    }

    public static WahooService getInstance()
    {
        return instance_;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        updateListeners("onCreate");
        super.onCreate();
        Log.d("inwahooService", "oncreate runned");
        instance_ = this;
    }

    public void startDiscovery(){
        mHardwareConnector = new HardwareConnector(this,mHardwareConnectorListener);
        mHardwareConnector.startDiscovery(this);
        updateListeners("start discovery");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDeviceDiscovered(@NonNull ConnectionParams connectionParams) {
        mSensorConnection = mHardwareConnector.requestSensorConnection(connectionParams,this);
        // Log.i(TAG, "onDeviceDiscovered");
        updateListeners("onDeviceDiscovered:"+ connectionParams.getName());
    }

    @Override
    public void onDiscoveredDeviceLost(@NonNull ConnectionParams connectionParams) {
        updateListeners("onDiscoveredDeviceLost");
    }

    @Override
    public void onDiscoveredDeviceRssiChanged(@NonNull ConnectionParams connectionParams, int i) {
        updateListeners("onDiscoveredDeviceRssiChanged");
    }

    @Override
    public void onSensorConnectionStateChanged(@NonNull SensorConnection sensorConnection, @NonNull HardwareConnectorEnums.SensorConnectionState sensorConnectionState) {
        updateListeners("onSensorConnectionStateChanged");
    }

    @Override
    public void onSensorConnectionError(@NonNull SensorConnection sensorConnection, @NonNull HardwareConnectorEnums.SensorConnectionError sensorConnectionError) {
        // Log.i(TAG, "onSensorConnectionError");
        updateListeners("onSensorConnectionError");
    }

    @Override
    public void onNewCapabilityDetected(@NonNull SensorConnection sensorConnection, @NonNull Capability.CapabilityType capabilityType) {
        if(capabilityType == Capability.CapabilityType.Heartrate)
        {
            Heartrate heartrate = (Heartrate) sensorConnection.getCurrentCapability(Capability.CapabilityType.Heartrate);
            heartrate.addListener(this);
            updateListeners("registered HR listener");
        }

        updateListeners(""+ capabilityType);
    }

    @Override
    public void onHeartrateData(@NonNull Heartrate.Data data) {
        updateListeners("onHeartRateData:" + getHRData());
    }

    public Heartrate.Data getHRData(){
        if ( mSensorConnection != null ) {
            Heartrate heartrate = ( Heartrate ) mSensorConnection.getCurrentCapability ( Capability.CapabilityType.Heartrate );
            if ( heartrate != null ) { return heartrate.getHeartrateData();
            } else {
                // The sensor connection does not currently support the heartrate capability
                updateListeners("not supported");
                return null;
            } } else {
            // Sensor not connected
            updateListeners("not connected");
            return null;
        }
    }

    private void updateListeners(String message){
        for(WahooServiceListener listener : listenerList)
        {
            listener.wahooEvent(message);
        }
    }

    @Override
    public void onHeartrateDataReset() {
        updateListeners("onHeartrateDataReset");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHardwareConnector.shutdown();
    }
}