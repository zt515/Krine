println("PATH = " + Core.getEnv("PATH"));

Core.setProperties(new String[] {"p1", "v1", "p2", "v2"});
println("p2 = " + Core.getProperty("p2"));

println("Core.getTime() = " + Core.getTime());

exitHook() {
    public void run() {
        println("Exiting");
    }
    return this;
}
Core.doOnExit(exitHook());

