# Started by name through app_process (see AudioProbe's doc); R8 cannot see that entry point.
-keep class com.openu60.agent.probe.AudioProbe { public static void main(java.lang.String[]); }
