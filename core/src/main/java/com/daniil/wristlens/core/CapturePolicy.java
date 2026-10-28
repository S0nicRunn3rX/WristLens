package com.daniil.wristlens.core;

public final class CapturePolicy {
    private CapturePolicy(){}
    public static String primaryAction(boolean connected,boolean video,boolean recording,boolean busy,int countdown,boolean holding){
        if(!connected||busy||countdown>0||holding)return "";
        return recording?"record_stop":video?"record_start":"photo";
    }
    public static boolean keepTimer(boolean background,String action){return background&&(action.equals("photo")||action.equals("video"));}
}
