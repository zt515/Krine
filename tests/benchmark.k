map = new HashMap();

public makeConsumer() {
    public void consume(it) {
        // System.out.println(233 + ((Integer) it.get("reimu")));
        int r = 233 + ((Integer) it.get("reimu"));
    }

    return this;
}

public makeFunction(name, value, mapConsumer) {
    public void call(int i) {
        map.put(name, value);
        mapConsumer.consume(map);
        map.remove(name);
    }

    return this;
}

public makeLooper(count, f) {
    public void loop() {
        int i = 0;
        while (i < count) {
            f.call(i);
            i = i + 1;
        }
    }

    return this;
}

for (int i = 0; i < 100; i++) {
    Profiler.begin("benchmark #" + i);
    makeLooper(200000, makeFunction("reimu", 100, makeConsumer())).loop();
    Profiler.end("benchmark #" + i);
}
