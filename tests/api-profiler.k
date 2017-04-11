Profiler.begin("hello");
Thread.sleep(50);
Profiler.begin("inner timer");
Thread.sleep(50);
Profiler.end("inner timer");
Profiler.end("hello");

void hello() {

}

Profiler.execute(this, "test_profiler", "hello();");
