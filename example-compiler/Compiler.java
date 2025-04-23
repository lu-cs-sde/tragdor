import ast.*;

public class Compiler {
    public static void main(String[] args) {
        Program p = buildProgram();

        System.out.println(p.getExpr().v());

        p.printAST();
    }

    public static Program buildProgram() {
        Expr e = new Add(new Add(new Num(10), new Num(20)), new Num(30));
        Program p = new Program(e);
        return p;
    }

    public static Object CodeProber_parse(String[] args) throws Exception {
        return buildProgram();
    }
}
