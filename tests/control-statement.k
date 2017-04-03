String s = "hello";
int i = 0;

if (s.equals("hello")) {
    for (; i < 10; i++) {
        s += i;
    }

    while (i < 15) {
        s += i++;
    }

    println(s);
}
