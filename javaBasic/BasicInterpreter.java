import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.script.*;

public class BasicInterpreter {
    private TreeMap<Integer, String> programLines = new TreeMap<>();
    private Map<String, Object> variables = new HashMap<>();
    private int currentLine;
    private boolean running;
    private Stack<Integer> callStack = new Stack<>();
    private ScriptEngine scriptEngine;

    public BasicInterpreter() {
        ScriptEngineManager manager = new ScriptEngineManager();
        this.scriptEngine = manager.getEngineByName("js");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java BasicInterpreter <file.bas>");
            return;
        }

        BasicInterpreter interpreter = new BasicInterpreter();
        interpreter.loadProgram(args[0]);
        interpreter.run();
    }

    public void loadProgram(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("'")) continue;

                if (Character.isDigit(line.charAt(0))) {
                    String[] parts = line.split("\\s+", 2);
                    int lineNumber = Integer.parseInt(parts[0]);
                    programLines.put(lineNumber, parts.length > 1 ? parts[1] : "");
                } else {
                    int nextLine = programLines.isEmpty() ? 10 : programLines.lastKey() + 10;
                    programLines.put(nextLine, line);
                }
            }
        } catch (IOException e) {
            System.err.println("File read error: " + e.getMessage());
            System.exit(1);
        }
    }

    public void run() {
        running = true;
        currentLine = programLines.firstKey();

        while (running && currentLine != -1) {
            String line = programLines.get(currentLine);
            if (line == null) {
                currentLine = getNextLine();
                continue;
            }

            executeLine(line);
        }
    }

    private void executeLine(String line) {
        String[] tokens = tokenize(line);
        if (tokens.length == 0) return;

        String command = tokens[0].toUpperCase();
        try {
            switch (command) {
                case "PRINT":
                    executePrint(tokens);
                    break;
                case "LET":
                    executeLet(tokens);
                    break;
                case "IF":
                    executeIf(tokens);
                    break;
                case "GOTO":
                    executeGoto(tokens);
                    break;
                case "FOR":
                    executeFor(tokens);
                    break;
                case "NEXT":
                    executeNext(tokens);
                    break;
                case "END":
                    running = false;
                    break;
                default:
                    if (line.contains("=")) {
                        executeAssignment(line);
                    } else {
                        System.err.println("Unknown command: " + command);
                    }
            }
        } catch (Exception e) {
            System.err.println("Error in line " + currentLine + ": " + e.getMessage());
            running = false;
        }

        currentLine = getNextLine();
    }

    private void executePrint(String[] tokens) {
        StringBuilder output = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.startsWith("\"") && token.endsWith("\"")) {
                output.append(token.substring(1, token.length() - 1));
            } else if (token.equals(",")) {
                output.append("\t");
            } else if (token.equals(";")) {
                // No space
            } else {
                Object value = evaluateExpression(token);
                output.append(value);
            }
        }
        System.out.println(output);
    }

    private void executeLet(String[] tokens) {
        if (tokens.length < 3 || !tokens[1].equals("=")) {
            throw new RuntimeException("Invalid LET syntax");
        }
        String varName = tokens[0];
        String expr = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length));
        variables.put(varName, evaluateExpression(expr));
    }

    private void executeIf(String[] tokens) {
        int thenIndex = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equalsIgnoreCase("THEN")) {
                thenIndex = i;
                break;
            }
        }
        if (thenIndex == -1) {
            throw new RuntimeException("Missing THEN in IF");
        }

        String condition = String.join(" ", Arrays.copyOfRange(tokens, 1, thenIndex));
        boolean result = evaluateCondition(condition);
        
        if (result) {
            String action = String.join(" ", Arrays.copyOfRange(tokens, thenIndex + 1, tokens.length));
            if (action.matches("\\d+")) {
                currentLine = Integer.parseInt(action);
            } else {
                executeLine(action);
            }
        }
    }

    private void executeGoto(String[] tokens) {
        if (tokens.length != 2) {
            throw new RuntimeException("Invalid GOTO syntax");
        }
        int targetLine = Integer.parseInt(tokens[1]);
        if (!programLines.containsKey(targetLine)) {
            throw new RuntimeException("Line " + targetLine + " not found");
        }
        currentLine = targetLine;
    }

    private String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"[^\"]*\"|\\d+\\.?\\d*|\\w+|[+\\-*/%=<>(),;]|\\S").matcher(line);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens.toArray(new String[0]);
    }

    private Object evaluateExpression(String expr) {
        try {
            return evalMathExpression(expr);
        } catch (Exception e) {
            if (variables.containsKey(expr)) {
                return variables.get(expr);
            }
            return expr;
        }
    }

    private double evalMathExpression(String expr) {
        expr = expr.replaceAll("\\s+", "");
        
        for (String var : variables.keySet()) {
            Object value = variables.get(var);
            if (value instanceof Number) {
                expr = expr.replaceAll("\\b" + var + "\\b", value.toString());
            }
        }
        
        try {
            Object result = scriptEngine.eval(expr.replace("^", "**"));
            return result instanceof Number ? ((Number)result).doubleValue() : 0;
        } catch (ScriptException e) {
            throw new RuntimeException("Error evaluating expression: " + expr, e);
        }
    }

    private int getNextLine() {
        Integer next = programLines.higherKey(currentLine);
        return next != null ? next : -1;
    }

    private void executeFor(String[] tokens) {
        if (tokens.length < 5 || !tokens[3].equalsIgnoreCase("TO")) {
            throw new RuntimeException("Invalid FOR syntax");
        }
        
        String var = tokens[1];
        double start = Double.parseDouble(tokens[2]);
        double end = Double.parseDouble(tokens[4]);
        double step = tokens.length > 6 && tokens[5].equalsIgnoreCase("STEP") ? 
                     Double.parseDouble(tokens[6]) : 1.0;
        
        variables.put(var, start);
        variables.put(var + "_end", end);
        variables.put(var + "_step", step);
        variables.put(var + "_loop_line", currentLine);
    }

    private void executeNext(String[] tokens) {
        if (tokens.length != 2) {
            throw new RuntimeException("Invalid NEXT syntax");
        }
        String var = tokens[1];
        
        double current = ((Number)variables.get(var)).doubleValue();
        double end = ((Number)variables.get(var + "_end")).doubleValue();
        double step = ((Number)variables.get(var + "_step")).doubleValue();
        
        current += step;
        variables.put(var, current);
        
        if ((step > 0 && current <= end) || (step < 0 && current >= end)) {
            currentLine = (Integer)variables.get(var + "_loop_line");
        }
    }

    private void executeAssignment(String line) {
        String[] parts = line.split("=", 2);
        String var = parts[0].trim();
        String expr = parts[1].trim();
        variables.put(var, evaluateExpression(expr));
    }

    private boolean evaluateCondition(String condition) {
        condition = condition.replaceAll("\\s+", "");
        
        String[] operators = {"<>", "<=", ">=", "<", ">", "="};
        for (String op : operators) {
            if (condition.contains(op)) {
                String[] parts = condition.split(Pattern.quote(op));
                if (parts.length != 2) continue;
                
                Object left = evaluateExpression(parts[0]);
                Object right = evaluateExpression(parts[1]);
                
                if (left instanceof Number && right instanceof Number) {
                    double l = ((Number)left).doubleValue();
                    double r = ((Number)right).doubleValue();
                    
                    switch (op) {
                        case "<": return l < r;
                        case ">": return l > r;
                        case "<=": return l <= r;
                        case ">=": return l >= r;
                        case "=": return l == r;
                        case "<>": return l != r;
                    }
                }
                return left.toString().equals(right.toString());
            }
        }
        throw new RuntimeException("Invalid condition: " + condition);
    }
}