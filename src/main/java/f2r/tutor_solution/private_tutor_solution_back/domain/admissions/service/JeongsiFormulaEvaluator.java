package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 정시 수능 환산 계산식(jeongsi_formula/v1) 계산기. buildit-admissions {@code scripts/jeongsi_formula.py}와 같은 문법·결과를 낸다
 * (일치 여부는 JeongsiFormulaEvaluatorConformanceTest가 계산식 전부로 확인한다).
 *
 * <p>문법: 숫자, 변수, {@code + - * /}, 괄호, 비교({@code == != < <= > >=}, 이어 쓰기 가능), {@code and or not},
 * 함수 {@code max min sum top_sum(k, …) cond(조건, 참, 거짓) table('표', 값) round(x, 자릿수) trunc(x, 자릿수)}.
 * 비교·논리식은 참 1, 거짓 0. round는 반올림(half-up), trunc는 버림.
 */
public final class JeongsiFormulaEvaluator {

    private JeongsiFormulaEvaluator() {}

    public static class FormulaException extends IllegalArgumentException {
        public FormulaException(String message) { super(message); }
    }

    private static final Set<String> FUNCTIONS = Set.of("max", "min", "round", "trunc", "cond", "table", "top_sum", "sum");

    /** 수식이 쓰는 변수 이름(함수 이름·표 이름 제외). */
    public static Set<String> variables(String expression) {
        Set<String> out = new LinkedHashSet<>();
        collect(parse(expression), out);
        return out;
    }

    public static double evaluate(String expression, Map<String, ? extends Number> inputs,
                                  Map<String, Map<String, ? extends Number>> tables) {
        double v = parse(expression).eval(inputs, tables == null ? Map.of() : tables);
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new FormulaException("결과가 숫자가 아님");
        return v;
    }

    // ---- AST ----
    private interface Node {
        double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> tables);
    }

    private record Num(double v) implements Node {
        public double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> t) { return v; }
    }

    private record Str(String v) implements Node {
        public double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> t) {
            throw new FormulaException("문자열은 table()의 표 이름에만 쓴다");
        }
    }

    private record Var(String name) implements Node {
        public double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> t) {
            Number n = in.get(name);
            if (n == null) throw new FormulaException("입력값 없음: " + name);
            return n.doubleValue();
        }
    }

    private record Unary(char op, Node a) implements Node {
        public double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> t) {
            double v = a.eval(in, t);
            return switch (op) { case '-' -> -v; case '+' -> v; default -> v != 0 ? 0.0 : 1.0; };
        }
    }

    private record Bin(char op, Node a, Node b) implements Node {
        public double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> t) {
            double x = a.eval(in, t), y = b.eval(in, t);
            return switch (op) {
                case '+' -> x + y;
                case '-' -> x - y;
                case '*' -> x * y;
                default -> { if (y == 0) throw new FormulaException("0으로 나눔"); yield x / y; }
            };
        }
    }

    private record Logic(boolean and, List<Node> parts) implements Node {
        public double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> t) {
            // 파이썬 판과 같이 모든 항을 계산한 뒤 판정한다(입력 누락 오류가 같은 조건에서 난다).
            List<Double> vals = new ArrayList<>();
            for (Node p : parts) vals.add(p.eval(in, t));
            return and ? (vals.stream().allMatch(v -> v != 0) ? 1.0 : 0.0)
                       : (vals.stream().anyMatch(v -> v != 0) ? 1.0 : 0.0);
        }
    }

    private record Compare(Node first, List<String> ops, List<Node> rest) implements Node {
        public double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> t) {
            double left = first.eval(in, t);
            for (int i = 0; i < ops.size(); i++) {
                double right = rest.get(i).eval(in, t);
                boolean ok = switch (ops.get(i)) {
                    case "==" -> left == right; case "!=" -> left != right; case "<" -> left < right;
                    case "<=" -> left <= right; case ">" -> left > right; default -> left >= right;
                };
                if (!ok) return 0.0;
                left = right;
            }
            return 1.0;
        }
    }

    private record Call(String fn, List<Node> args) implements Node {
        public double eval(Map<String, ? extends Number> in, Map<String, Map<String, ? extends Number>> t) {
            switch (fn) {
                case "cond" -> {
                    if (args.size() != 3) throw new FormulaException("cond(조건, 참일 때, 거짓일 때)");
                    return args.get(0).eval(in, t) != 0 ? args.get(1).eval(in, t) : args.get(2).eval(in, t);
                }
                case "table" -> {
                    if (args.size() != 2 || !(args.get(0) instanceof Str s)) throw new FormulaException("table은 table('표이름', 값) 형태여야 한다");
                    Map<String, ? extends Number> tb = t.get(s.v());
                    if (tb == null) throw new FormulaException("표 없음: " + s.v());
                    double key = args.get(1).eval(in, t);
                    String k = key == Math.rint(key) ? String.valueOf((long) key) : pyRepr(key);
                    Number v = tb.get(k);
                    if (v == null) throw new FormulaException("표 " + s.v() + "에 " + k + " 값 없음");
                    return v.doubleValue();
                }
                default -> { }
            }
            List<Double> v = new ArrayList<>();
            for (Node a : args) v.add(a.eval(in, t));
            return switch (fn) {
                case "max" -> v.stream().mapToDouble(Double::doubleValue).max().orElseThrow(() -> new FormulaException("max에 인자가 없음"));
                case "min" -> v.stream().mapToDouble(Double::doubleValue).min().orElseThrow(() -> new FormulaException("min에 인자가 없음"));
                case "sum" -> v.stream().mapToDouble(Double::doubleValue).sum();
                case "top_sum" -> {
                    int k = (int) v.get(0).doubleValue();
                    yield v.subList(1, v.size()).stream().sorted((a, b) -> Double.compare(b, a)).limit(Math.max(k, 0))
                            .mapToDouble(Double::doubleValue).sum();
                }
                case "round", "trunc" -> {
                    int digits = v.size() > 1 ? (int) v.get(1).doubleValue() : 0;
                    yield new BigDecimal(pyRepr(v.get(0)))
                            .setScale(digits, "round".equals(fn) ? RoundingMode.HALF_UP : RoundingMode.DOWN).doubleValue();
                }
                default -> throw new FormulaException("허용되지 않는 함수: " + fn);
            };
        }
    }

    /** 파이썬 str(float)처럼 가장 짧은 표기(자바 21의 Double.toString도 가장 짧은 표기다). */
    private static String pyRepr(double d) {
        return Double.toString(d);
    }

    private static void collect(Node n, Set<String> out) {
        switch (n) {
            case Var v -> out.add(v.name());
            case Unary u -> collect(u.a(), out);
            case Bin b -> { collect(b.a(), out); collect(b.b(), out); }
            case Logic l -> l.parts().forEach(p -> collect(p, out));
            case Compare c -> { collect(c.first(), out); c.rest().forEach(p -> collect(p, out)); }
            case Call c -> c.args().forEach(p -> collect(p, out));
            default -> { }
        }
    }

    // ---- 파서 (재귀 하강) ----
    static Node parse(String expression) {
        if (expression == null || expression.isBlank()) throw new FormulaException("수식이 비었음");
        Parser p = new Parser(tokenize(expression));
        Node n = p.or();
        if (p.pos != p.toks.size()) throw new FormulaException("수식 문법 오류: '" + p.toks.get(p.pos).text + "' 근처");
        return n;
    }

    private record Tok(char kind, String text) {}   // kind: n 숫자, s 문자열, i 이름, o 연산자

    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                int j = i;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                if (j < s.length() && (s.charAt(j) == 'e' || s.charAt(j) == 'E')) {
                    int k = j + 1;
                    if (k < s.length() && (s.charAt(k) == '+' || s.charAt(k) == '-')) k++;
                    if (k < s.length() && Character.isDigit(s.charAt(k))) {
                        j = k;
                        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                    }
                }
                out.add(new Tok('n', s.substring(i, j)));
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                out.add(new Tok('i', s.substring(i, j)));
                i = j;
            } else if (c == '\'' || c == '"') {
                int j = s.indexOf(c, i + 1);
                if (j < 0) throw new FormulaException("수식 문법 오류: 닫히지 않은 문자열");
                out.add(new Tok('s', s.substring(i + 1, j)));
                i = j + 1;
            } else {
                String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
                if (List.of("==", "!=", "<=", ">=").contains(two)) { out.add(new Tok('o', two)); i += 2; }
                else if ("+-*/(),<>".indexOf(c) >= 0) { out.add(new Tok('o', String.valueOf(c))); i++; }
                else throw new FormulaException("허용되지 않는 문자: " + c);
            }
        }
        return out;
    }

    private static final class Parser {
        final List<Tok> toks;
        int pos;

        Parser(List<Tok> toks) { this.toks = toks; }

        Tok peek() { return pos < toks.size() ? toks.get(pos) : null; }
        boolean isOp(String o) { Tok t = peek(); return t != null && t.kind == 'o' && t.text.equals(o); }
        boolean isWord(String w) { Tok t = peek(); return t != null && t.kind == 'i' && t.text.equals(w); }
        void expect(String o) {
            if (!isOp(o)) throw new FormulaException("수식 문법 오류: '" + o + "'가 필요함");
            pos++;
        }

        Node or() {
            List<Node> parts = new ArrayList<>(List.of(and()));
            while (isWord("or")) { pos++; parts.add(and()); }
            return parts.size() == 1 ? parts.get(0) : new Logic(false, parts);
        }

        Node and() {
            List<Node> parts = new ArrayList<>(List.of(not()));
            while (isWord("and")) { pos++; parts.add(not()); }
            return parts.size() == 1 ? parts.get(0) : new Logic(true, parts);
        }

        Node not() {
            if (isWord("not")) { pos++; return new Unary('!', not()); }
            return compare();
        }

        Node compare() {
            Node first = add();
            List<String> ops = new ArrayList<>();
            List<Node> rest = new ArrayList<>();
            while (peek() != null && peek().kind == 'o' && List.of("==", "!=", "<", "<=", ">", ">=").contains(peek().text)) {
                ops.add(toks.get(pos++).text);
                rest.add(add());
            }
            return ops.isEmpty() ? first : new Compare(first, ops, rest);
        }

        Node add() {
            Node n = mul();
            while (isOp("+") || isOp("-")) { char op = toks.get(pos++).text.charAt(0); n = new Bin(op, n, mul()); }
            return n;
        }

        Node mul() {
            Node n = unary();
            while (isOp("*") || isOp("/")) { char op = toks.get(pos++).text.charAt(0); n = new Bin(op, n, unary()); }
            return n;
        }

        Node unary() {
            if (isOp("-") || isOp("+")) { char op = toks.get(pos++).text.charAt(0); return new Unary(op, unary()); }
            return primary();
        }

        Node primary() {
            Tok t = peek();
            if (t == null) throw new FormulaException("수식 문법 오류: 수식이 끝남");
            if (t.kind == 'n') {
                pos++;
                try { return new Num(Double.parseDouble(t.text)); }
                catch (NumberFormatException e) { throw new FormulaException("숫자 형식 오류: " + t.text); }
            }
            if (t.kind == 's') { pos++; return new Str(t.text); }
            if (isOp("(")) { pos++; Node n = or(); expect(")"); return n; }
            if (t.kind == 'i') {
                if (List.of("and", "or", "not").contains(t.text)) throw new FormulaException("수식 문법 오류: '" + t.text + "' 위치");
                pos++;
                if (isOp("(")) {
                    if (!FUNCTIONS.contains(t.text)) throw new FormulaException("허용되지 않는 함수: " + t.text);
                    pos++;
                    List<Node> args = new ArrayList<>();
                    if (!isOp(")")) {
                        args.add(or());
                        while (isOp(",")) { pos++; args.add(or()); }
                    }
                    expect(")");
                    return new Call(t.text, args);
                }
                return new Var(t.text);
            }
            throw new FormulaException("수식 문법 오류: '" + t.text + "' 위치");
        }
    }
}
