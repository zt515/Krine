println("PATH = " + Core.getEnv("PATH"));

Core.setProperties(new String[] {"p1", "v1", "p2", "v2"});
println("p2 = " + Core.getProperty("p2"));

println("Core.getTime() = " + Core.getTime());

private exitHook() {
    public void run() {
        println("[Exit Hook]: Exiting");
    }
    return this;
}
Core.doOnExit(exitHook());

