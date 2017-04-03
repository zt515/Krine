class App {
    public App() {
        println("Constructor");
    }

    public void main() {
        println("App.main");
    }

    public static void launch() {
        println("App::launch");
        new App().main();
    }
}

App.launch();
