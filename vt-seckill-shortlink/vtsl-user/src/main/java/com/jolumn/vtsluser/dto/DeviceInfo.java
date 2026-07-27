package com.jolumn.vtsluser.dto;

public class DeviceInfo {

    private String deviceId;
    private boolean current;

    public static DeviceInfo of(String deviceId, boolean current) {
        DeviceInfo d = new DeviceInfo();
        d.deviceId = deviceId;
        d.current = current;
        return d;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }
}
