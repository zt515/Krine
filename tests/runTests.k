println("Krine Tests Runner");
println("------------------");

File self = new File(krine.args[0]);
File testsDir = self.getParentFile();

String[] args = new String[1];
for (File file : testsDir.listFiles()) {
    if (file.equals(self)) {
        continue;
    }

    println("=> Running " + file.getName());
    args[0] = file.getAbsolutePath();
    com.krine.command.Main.main(args);
    println("");
}
