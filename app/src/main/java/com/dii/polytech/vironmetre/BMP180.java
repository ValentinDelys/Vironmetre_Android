package com.dii.polytech.vironmetre;

import android.util.Log;

/**
 * Created by Valentin on 5/27/2016.
 */
public class BMP180 implements DeviceControl.DeviceInterface{
    
    private final byte REG_CONTROL = (byte)0xF4;
    private final byte REG_RESULT = (byte)0xF6;
    private final byte REG_AC1 = (byte)0xAA;
    private final byte REG_AC2 = (byte)0xAC;
    private final byte REG_AC3 = (byte)0xAE;
    private final byte REG_AC4 = (byte)0xB0;
    private final byte REG_AC5 = (byte)0xB2;
    private final byte REG_AC6 = (byte)0xB4;
    private final byte REG_B1 = (byte)0xB6;
    private final byte REG_B2 = (byte)0xB8;
    private final byte REG_MB = (byte)0xBA;
    private final byte REG_MC = (byte)0xBC;
    private final byte REG_MD = (byte)0xBE;

    private final byte COMMAND_TEMPERATURE = (byte)0x2E;
    private final byte COMMAND_PRESSURE0 = (byte)0x34;
    private final byte COMMAND_PRESSURE1 = (byte)0x74;
    private final byte COMMAND_PRESSURE2 = (byte)0xB4;
    private final byte COMMAND_PRESSURE3 = (byte)0xF4;

    private short AC1, AC2, AC3, VB1, VB2, MB, MC, MD, AC4, AC5, AC6;

    private double c5, c6, mc, md, x0, x1, x2, y0, y1, y2, p0, p1, p2;

    private DeviceControl mControl = null;

    enum state
    {
        init,
        state1,
        state2,
        state3,
        state4
    }

    private state theState = state.init;

    public BMP180(DeviceControl control){
        mControl = control;
    }

    @Override
    public void Init() {
        Log.d("BMP180","Init");
        byte[] writeBytes = new byte[] {(byte)REG_AC1};
        mControl.WriteBytes(writeBytes, (byte)writeBytes.length);
    }

    @Override
    public void getData(byte[] data) {
        Log.d("Write BMP180", "");
        for(int i = 0; i < data.length; i++)
            Log.d("     ",Integer.toHexString(data[i]));

        byte[] bytes;
        short value = 0;

        switch(theState)
        {
            case init:
                theState = state.state1;
                bytes = new byte[]{};
                mControl.ReadBytes((byte)2);
                break;
            case state1:
                break;
            case state2:
                break;
            case state3:
                break;
            case state4:
                break;
        }


    }
}
