package xyz.thelegacyvoyage.hyessentialsx.models;

public final class IpHistoryModel {
    private String ip;
    private long lastUsed;

    @SuppressWarnings("unused")
    public IpHistoryModel() {}

    public IpHistoryModel(String ip, long lastUsed) {
        this.ip = ip;
        this.lastUsed = lastUsed;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(long lastUsed) {
        this.lastUsed = Math.max(0L, lastUsed);
    }

    @Override
    public String toString() {
        return "IpHistoryModel{ip='" + ip + "', lastUsed=" + lastUsed + "}";
    }
}
