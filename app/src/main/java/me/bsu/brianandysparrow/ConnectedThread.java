package me.bsu.brianandysparrow;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.List;

import me.bsu.proto.Feature;

/**
 * Created by aschmitt on 1/31/16.
 * This thread acts as an interface to the bluetooth's data input and output streams.
 * It will constanlty listen and can also be written to.
 * This is where we can potentially put all of the proto-buff code.
 */
class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final DataInputStream mmInStream;
    private final DataOutputStream mmOutStream;
    private final Handler mHandler;
    private final String macAddress;
    // Used for connections that support vector clocks
    private UUID userUUID = null;
    private List<Feature> features = null;

    public ConnectedThread(BluetoothSocket socket, Handler dataReceivedHandler) {
        mHandler = dataReceivedHandler;
        mmSocket = socket;
        macAddress = socket.getRemoteDevice().getAddress();

        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output streams, using temp objects because
        // member streams are final
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }

        mmInStream = new DataInputStream(tmpIn);
        mmOutStream = new DataOutputStream(tmpOut);
    }

    public void run() {
        byte[] buffer = new byte[1024];  // buffer store for the stream
        int bytes; // bytes returned from read()

        // Keep listening to the InputStream until an exception occurs
        byte[] result;
        while (true) {
            try {
                // Read the length of the incoming data from the InputStream
                bytes = mmInStream.readInt();
                result = Util.readBytesFromStream(mmInStream, bytes);

                // Send the obtained bytes to the UI activity
                mHandler.obtainMessage(0, this.new ConnectionData(mmSocket, result))
                        .sendToTarget();
            } catch (IOException e) {
                break;
            }
        }
    }

    /**
     * Call this from the main activity to shutdown the connection
     * */
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) { }
    }

    /**
     * This class represents data that is being passed from a connection to this device.
     * Think of this as a data packet that has access to the thread it came from
     */
    class ConnectionData {

        private byte[] data;
        private BluetoothSocket socket;

        ConnectionData(BluetoothSocket s, byte[] d) {
            socket = s;
            data = d;
        }

        public byte[] getData() {
            return data;
        }

        public ConnectedThread getConnection() {
            return ConnectedThread.this;
        }
    }

    /***********************
     * GETTERS AND SETTERS *
     ***********************/

    public String getID() {
        return macAddress;
    }

    public UUID getUUID() {
        return userUUID;
    }

    public void setUUID(UUID uuid) {
        userUUID = uuid;
    }

    public void setFeatures(List<Feature> f) {
        features = f;
    }

    public BluetoothSocket getSocket() {
        return mmSocket;
    }

    /*******************
     * WRITING METHODS *
     *******************/

    public void write(byte[] bytes) {
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) { }
    }

    public void writeInt(int i) {
        try {
            mmOutStream.writeInt(i);
        } catch(IOException e) { }
    }

}
