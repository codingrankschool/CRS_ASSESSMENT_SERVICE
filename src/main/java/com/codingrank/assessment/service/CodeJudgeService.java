package com.codingrank.assessment.service;

import com.codingrank.assessment.common.enums.SubmissionStatus;
import com.codingrank.assessment.dto.ExecutionResultDto;
import com.codingrank.assessment.model.Question;
import com.codingrank.assessment.model.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CodeJudgeService {

    @Value("${assessment.judge.execution-timeout-ms:5000}")
    private long executionTimeoutMs;

    @Value("${assessment.judge.allow-unsandboxed:false}")
    private boolean allowUnsandboxed;

    @Value("${assessment.judge.docker-enabled:false}")
    private boolean dockerEnabled;

    /**
     * Executes user code against a suite of test cases.
     */
    public ExecutionResultDto evaluate(Question question, List<TestCase> testCases, String code, String language) {
        List<ExecutionResultDto.TestCaseResult> results = new ArrayList<>();

        if (testCases == null || testCases.isEmpty()) {
            log.info("No test cases found for questionId={}. Marking as passed.", question.getId());
            return ExecutionResultDto.builder()
                    .status(SubmissionStatus.PASSED)
                    .score(question.getPoints())
                    .testCasesPassed(0)
                    .testCasesTotal(0)
                    .runtimeMs(0L)
                    .compilerOutput("No test cases specified")
                    .testCaseDetails(results)
                    .build();
        }

        log.info("Starting code evaluation: questionId='{}' language='{}' testCasesCount={}",
                question.getId(), language, testCases.size());

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("judge_" + UUID.randomUUID());
            String fileName = getFileName(language);
            Path sourceFile = workDir.resolve(fileName);
            String wrappedCode = wrapCodeIfNeeded(code, language, question);
            Files.writeString(sourceFile, wrappedCode);

            // Compile if necessary
            String compileCommand = getCompileCommand(language, sourceFile);
            if (compileCommand != null) {
                log.debug("Compiling code with command: '{}' in workDir: '{}'", compileCommand, workDir);
                Process compileProcess = new ProcessBuilder(compileCommand.split(" "))
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .start();

                boolean finished = compileProcess.waitFor(executionTimeoutMs, TimeUnit.MILLISECONDS);
                if (!finished || compileProcess.exitValue() != 0) {
                    String compileErr = new String(compileProcess.getInputStream().readAllBytes());
                    compileProcess.destroyForcibly();
                    log.warn("Compilation failed for questionId='{}': {}", question.getId(), compileErr.trim());
                    return ExecutionResultDto.builder()
                            .status(SubmissionStatus.CE)
                            .score(0)
                            .testCasesPassed(0)
                            .testCasesTotal(testCases.size())
                            .compilerOutput(compileErr.isBlank() ? "Compilation Error" : compileErr)
                            .testCaseDetails(results)
                            .build();
                }
                log.debug("Compilation successful for questionId='{}'", question.getId());
            }

            int passedCount = 0;
            long maxRuntime = 0;
            SubmissionStatus overallStatus = SubmissionStatus.PASSED;

            for (TestCase tc : testCases) {
                long start = System.currentTimeMillis();
                ExecutionResultDto.TestCaseResult tcResult = runSingleTestCase(workDir, language, tc);
                long elapsed = System.currentTimeMillis() - start;
                maxRuntime = Math.max(maxRuntime, elapsed);

                results.add(tcResult);
                if (tcResult.isPassed()) {
                    passedCount++;
                } else {
                    if (overallStatus == SubmissionStatus.PASSED) {
                        overallStatus = SubmissionStatus.FAILED;
                    }
                    if (results.size() <= 3) {
                        log.info("Test case #{} failed: input='{}' expected='{}' actual='{}'",
                                results.size(), tc.isHidden() ? "<hidden>" : tc.getInput(),
                                tc.getExpectedOutput(), tcResult.getActualOutput());
                    }
                }
            }

            int calculatedScore = testCases.isEmpty() ? 0 : (int) Math.round(((double) passedCount / testCases.size()) * question.getPoints());

            SubmissionStatus finalStatus = passedCount == testCases.size() ? SubmissionStatus.PASSED : overallStatus;
            log.info("Code evaluation finished for questionId='{}': status={} passed={}/{} score={} maxRuntime={}ms",
                    question.getId(), finalStatus, passedCount, testCases.size(), calculatedScore, maxRuntime);

            return ExecutionResultDto.builder()
                    .status(finalStatus)
                    .score(calculatedScore)
                    .testCasesPassed(passedCount)
                    .testCasesTotal(testCases.size())
                    .runtimeMs(maxRuntime)
                    .compilerOutput("Evaluation Complete")
                    .testCaseDetails(results)
                    .build();

        } catch (Exception e) {
            log.error("Execution error occurred during code evaluation for questionId='{}'", question.getId(), e);
            return ExecutionResultDto.builder()
                    .status(SubmissionStatus.RTE)
                    .score(0)
                    .testCasesPassed(0)
                    .testCasesTotal(testCases.size())
                    .compilerOutput("Execution Exception: " + e.getMessage())
                    .testCaseDetails(results)
                    .build();
        } finally {
            cleanupDirectory(workDir);
        }
    }

    private ExecutionResultDto.TestCaseResult runSingleTestCase(Path workDir, String language, TestCase tc) {
        String runCommand = getRunCommand(language);
        Process process = null;
        try {
            process = new ProcessBuilder(runCommand.split(" "))
                    .directory(workDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            if (tc.getInput() != null && !tc.getInput().isEmpty()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(tc.getInput().getBytes());
                    os.flush();
                }
            }

            boolean completed = process.waitFor(executionTimeoutMs, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ExecutionResultDto.TestCaseResult.builder()
                        .input(tc.isHidden() ? "<hidden>" : tc.getInput())
                        .expectedOutput(tc.isHidden() ? "<hidden>" : tc.getExpectedOutput())
                        .actualOutput("Time Limit Exceeded (" + executionTimeoutMs + "ms)")
                        .passed(false)
                        .hidden(tc.isHidden())
                        .build();
            }

            String actualOutput = new String(process.getInputStream().readAllBytes()).trim();
            String expected = tc.getExpectedOutput() != null ? tc.getExpectedOutput().trim() : "";
            boolean passed = outputsMatch(actualOutput, expected);

            return ExecutionResultDto.TestCaseResult.builder()
                    .input(tc.isHidden() ? "<hidden>" : tc.getInput())
                    .expectedOutput(tc.isHidden() ? "<hidden>" : expected)
                    .actualOutput(tc.isHidden() ? "<hidden>" : actualOutput)
                    .passed(passed)
                    .hidden(tc.isHidden())
                    .build();

        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return ExecutionResultDto.TestCaseResult.builder()
                    .input(tc.isHidden() ? "<hidden>" : tc.getInput())
                    .expectedOutput(tc.isHidden() ? "<hidden>" : tc.getExpectedOutput())
                    .actualOutput("Runtime Error: " + e.getMessage())
                    .passed(false)
                    .hidden(tc.isHidden())
                    .build();
        }
    }

    private String getFileName(String language) {
        return switch (language.toLowerCase().trim()) {
            case "python", "py" -> "Solution.py";
            case "java" -> "Main.java";
            case "cpp", "c++", "cc" -> "solution.cpp";
            case "c" -> "solution.c";
            case "javascript", "js", "node" -> "solution.js";
            case "typescript", "ts" -> "solution.ts";
            default -> "solution.txt";
        };
    }

    private String getCompileCommand(String language, Path file) {
        return switch (language.toLowerCase().trim()) {
            case "java" -> "javac Main.java";
            case "cpp", "c++", "cc" -> "g++ -O2 -std=c++17 solution.cpp -o solution";
            case "c" -> "gcc -O2 solution.c -o solution -lm";
            default -> null; // interpreted
        };
    }

    private String getRunCommand(String language) {
        return switch (language.toLowerCase().trim()) {
            case "python", "py" -> "python3 -u Solution.py";
            case "java" -> "java -Xmx128m Main";
            case "cpp", "c++", "cc", "c" -> "./solution";
            case "javascript", "js", "node" -> "node solution.js";
            case "typescript", "ts" -> "node --experimental-strip-types solution.ts";
            default -> "cat";
        };
    }

    private void cleanupDirectory(Path dir) {
        if (dir != null && Files.exists(dir)) {
            try {
                Files.walk(dir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
            } catch (Exception ignored) {}
        }
    }

    private boolean outputsMatch(String actual, String expected) {
        if (actual == null && expected == null) return true;
        if (actual == null || expected == null) return false;
        String a = actual.trim();
        String e = expected.trim();
        if (a.equals(e)) return true;

        // Boolean case-insensitivity: true / True / false / False
        if (a.equalsIgnoreCase(e) && (a.equalsIgnoreCase("true") || a.equalsIgnoreCase("false"))) {
            return true;
        }

        // Line-ending tolerance and trailing whitespace stripping
        String aNorm = a.replace("\r\n", "\n").replaceAll("\\s+$", "");
        String eNorm = e.replace("\r\n", "\n").replaceAll("\\s+$", "");
        if (aNorm.equals(eNorm)) return true;

        // Strip enclosing quotes: "foo" vs foo or 'foo' vs foo
        if ((e.startsWith("\"") && e.endsWith("\"") && e.length() >= 2) || (e.startsWith("'") && e.endsWith("'") && e.length() >= 2)) {
            String strippedE = e.substring(1, e.length() - 1);
            if (a.equals(strippedE)) return true;
        }
        if ((a.startsWith("\"") && a.endsWith("\"") && a.length() >= 2) || (a.startsWith("'") && a.endsWith("'") && a.length() >= 2)) {
            String strippedA = a.substring(1, a.length() - 1);
            if (strippedA.equals(e)) return true;
        }

        // Array / JSON bracket normalization: [0, 1] vs [0,1]
        if (a.startsWith("[") && a.endsWith("]") && e.startsWith("[") && e.endsWith("]")) {
            String aClean = a.replaceAll("\\s+", "");
            String eClean = e.replaceAll("\\s+", "");
            if (aClean.equals(eClean)) return true;
        }

        // Object / JSON brace normalization: {"a": 1} vs {"a":1}
        if (a.startsWith("{") && a.endsWith("}") && e.startsWith("{") && e.endsWith("}")) {
            String aClean = a.replaceAll("\\s+", "");
            String eClean = e.replaceAll("\\s+", "");
            if (aClean.equals(eClean)) return true;
        }

        // Numerical equivalence (e.g., 2.0 vs 2)
        try {
            double numA = Double.parseDouble(a);
            double numE = Double.parseDouble(e);
            if (Math.abs(numA - numE) < 1e-6) return true;
        } catch (NumberFormatException ignored) {}

        return false;
    }

    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            "main", "if", "while", "for", "switch", "catch", "return", "new", "class", "struct",
            "int", "void", "double", "float", "string", "bool", "char", "long", "public", "private", "protected",
            "import", "package", "const", "let", "var", "function", "def", "static", "final"
    );

    private String extractTargetFunctionName(String code, Question question) {
        if (question != null && question.getFunctionSignature() != null && !question.getFunctionSignature().isBlank()) {
            Matcher m = Pattern.compile("([a-zA-Z0-9_$]+)\\s*\\(").matcher(question.getFunctionSignature().trim());
            if (m.find()) {
                String name = m.group(1);
                if (!RESERVED_KEYWORDS.contains(name)) return name;
            }
        }
        if (code != null) {
            Matcher m1 = Pattern.compile("(?:def|function)\\s+([a-zA-Z0-9_$]+)\\s*\\(").matcher(code);
            if (m1.find()) return m1.group(1);

            Matcher m2 = Pattern.compile("(?:const|let|var)\\s+([a-zA-Z0-9_$]+)\\s*=").matcher(code);
            if (m2.find()) return m2.group(1);

            Matcher m3 = Pattern.compile("(?:public|int|void|string|char\\*|bool|vector<[\\w]+>|Integer|String|List<[\\w]+>)\\s+([a-zA-Z0-9_$]+)\\s*\\(").matcher(code);
            while (m3.find()) {
                String name = m3.group(1);
                if (!RESERVED_KEYWORDS.contains(name)) return name;
            }
        }
        return "solve";
    }

    private List<String> extractParamTypes(String code, Question question) {
        List<String> list = new ArrayList<>();
        String sig = question != null ? question.getFunctionSignature() : null;
        if (sig != null && sig.contains("(") && sig.contains(")")) {
            int start = sig.indexOf('(');
            int end = sig.indexOf(')', start);
            String inner = sig.substring(start + 1, end).trim();
            if (!inner.isEmpty()) {
                for (String p : inner.split(",")) {
                    p = p.trim();
                    if (p.contains(":")) {
                        String[] parts = p.split(":");
                        list.add(parts[1].trim().toLowerCase());
                    } else {
                        String lower = p.toLowerCase();
                        if (lower.contains("vector<vector<int>>") || lower.contains("int[][]")) {
                            list.add("number[][]");
                        } else if (lower.contains("vector<int>") || lower.contains("int[]") || lower.contains("number[]") || lower.contains("vector<long>")) {
                            list.add("number[]");
                        } else if (lower.contains("vector<string>") || lower.contains("string[]")) {
                            list.add("string[]");
                        } else if (lower.contains("int") || lower.contains("long") || lower.contains("double") || lower.contains("float") || lower.contains("number")) {
                            list.add("number");
                        } else if (lower.contains("bool")) {
                            list.add("boolean");
                        } else {
                            list.add("string");
                        }
                    }
                }
                return list;
            }
        }
        if (code != null) {
            String targetFn = extractTargetFunctionName(code, question);
            Pattern fnPattern = Pattern.compile("\\b" + Pattern.quote(targetFn) + "\\s*\\(([^)]*)\\)");
            Matcher m = fnPattern.matcher(code);
            if (m.find()) {
                String inner = m.group(1).trim();
                if (!inner.isEmpty()) {
                    for (String p : inner.split(",")) {
                        String lower = p.trim().toLowerCase();
                        if (lower.contains("vector<vector<int>>") || lower.contains("int[][]")) {
                            list.add("number[][]");
                        } else if (lower.contains("vector<int>") || lower.contains("int[]") || lower.contains("number[]")) {
                            list.add("number[]");
                        } else if (lower.contains("vector<string>") || lower.contains("string[]")) {
                            list.add("string[]");
                        } else if (lower.contains("int") || lower.contains("long") || lower.contains("double") || lower.contains("float") || lower.contains("number")) {
                            list.add("number");
                        } else if (lower.contains("bool")) {
                            list.add("boolean");
                        } else {
                            list.add("string");
                        }
                    }
                    return list;
                }
            }
        }
        return list;
    }

    private String wrapCodeIfNeeded(String code, String language, Question question) {
        if (code == null) return "";
        String lang = language != null ? language.toLowerCase().trim() : "";

        // ===================== PYTHON =====================
        if (lang.equals("python") || lang.equals("py")) {
            if (code.contains("sys.stdin") || code.contains("input(")) {
                return code;
            }
            List<String> params = extractParamTypes(code, question);
            String p0 = params.isEmpty() ? "" : params.get(0);

            return code + "\n\n"
                    + "if __name__ == '__main__':\n"
                    + "    import sys, json, inspect, ast\n"
                    + "    raw = sys.stdin.read().strip()\n"
                    + "    target_func = None\n"
                    + "    if 'Solution' in globals() and inspect.isclass(globals()['Solution']):\n"
                    + "        try:\n"
                    + "            sol = globals()['Solution']()\n"
                    + "            methods = [getattr(sol, m) for m in dir(sol) if callable(getattr(sol, m)) and not m.startswith('_')]\n"
                    + "            if methods:\n"
                    + "                target_func = methods[0]\n"
                    + "        except Exception:\n"
                    + "            pass\n"
                    + "    if not target_func:\n"
                    + "        funcs = [obj for name, obj in list(globals().items()) if inspect.isfunction(obj) and not name.startswith('_')]\n"
                    + "        if funcs:\n"
                    + "            target_func = funcs[-1]\n"
                    + "    if target_func:\n"
                    + "        parsed = None\n"
                    + "        has_parsed = False\n"
                    + "        for parser in [\n"
                    + "            lambda r: json.loads(r),\n"
                    + "            lambda r: ast.literal_eval(r),\n"
                    + "            lambda r: json.loads(f'[{r}]'),\n"
                    + "            lambda r: ast.literal_eval(f'({r})')\n"
                    + "        ]:\n"
                    + "            try:\n"
                    + "                parsed = parser(raw)\n"
                    + "                has_parsed = True\n"
                    + "                break\n"
                    + "            except Exception:\n"
                    + "                pass\n"
                    + "        if not has_parsed:\n"
                    + "            parsed = raw\n"
                    + "\n"
                    + "        p0_type = '" + p0 + "'\n"
                    + "        if p0_type == 'string':\n"
                    + "            if isinstance(parsed, (list, tuple)) and len(parsed) == 1 and isinstance(parsed[0], str):\n"
                    + "                parsed = parsed[0]\n"
                    + "            elif not isinstance(parsed, str):\n"
                    + "                parsed = str(raw)\n"
                    + "        elif '[]' in p0_type or 'vector' in p0_type or 'list' in p0_type:\n"
                    + "            if not isinstance(parsed, list):\n"
                    + "                parsed = [parsed]\n"
                    + "\n"
                    + "        try:\n"
                    + "            sig = inspect.signature(target_func)\n"
                    + "            params = [p for p in sig.parameters.values() if p.kind in (inspect.Parameter.POSITIONAL_ONLY, inspect.Parameter.POSITIONAL_OR_KEYWORD)]\n"
                    + "            param_count = len(params)\n"
                    + "        except Exception:\n"
                    + "            param_count = -1\n"
                    + "\n"
                    + "        try:\n"
                    + "            if param_count == 1:\n"
                    + "                try:\n"
                    + "                    res = target_func(parsed)\n"
                    + "                except TypeError:\n"
                    + "                    args = parsed if isinstance(parsed, (list, tuple)) else [parsed]\n"
                    + "                    res = target_func(*args)\n"
                    + "            elif param_count > 1 and isinstance(parsed, (list, tuple)) and len(parsed) == param_count:\n"
                    + "                res = target_func(*parsed)\n"
                    + "            else:\n"
                    + "                args = parsed if isinstance(parsed, (list, tuple)) else [parsed]\n"
                    + "                try:\n"
                    + "                    res = target_func(*args)\n"
                    + "                except TypeError:\n"
                    + "                    res = target_func(parsed)\n"
                    + "\n"
                    + "            if isinstance(res, bool):\n"
                    + "                print('true' if res else 'false')\n"
                    + "            elif isinstance(res, (dict, list)):\n"
                    + "                print(json.dumps(res))\n"
                    + "            else:\n"
                    + "                print(res)\n"
                    + "        except Exception as e:\n"
                    + "            print(f'Error: {e}', file=sys.stderr)\n"
                    + "            sys.exit(1)\n";
        }

        // ===================== JAVASCRIPT / TYPESCRIPT =====================
        if (lang.equals("javascript") || lang.equals("js") || lang.equals("node") || lang.equals("typescript") || lang.equals("ts")) {
            if (code.contains("readline") || code.contains("process.stdin")) {
                return code;
            }
            String targetFn = extractTargetFunctionName(code, question);
            List<String> params = extractParamTypes(code, question);
            String p0 = params.isEmpty() ? "" : params.get(0);

            return code + "\n\n"
                    + "const fs = require('fs');\n"
                    + "const raw = fs.readFileSync(0, 'utf-8').trim();\n"
                    + "let fn = null;\n"
                    + "if (typeof Solution !== 'undefined') {\n"
                    + "    try {\n"
                    + "        const sol = new Solution();\n"
                    + "        if (typeof sol['" + targetFn + "'] === 'function') {\n"
                    + "            fn = sol['" + targetFn + "'].bind(sol);\n"
                    + "        } else {\n"
                    + "            for (const k of Object.getOwnPropertyNames(Object.getPrototypeOf(sol))) {\n"
                    + "                if (k !== 'constructor' && typeof sol[k] === 'function') { fn = sol[k].bind(sol); break; }\n"
                    + "            }\n"
                    + "        }\n"
                    + "    } catch(e) {}\n"
                    + "}\n"
                    + "if (!fn) {\n"
                    + "    try { if (typeof " + targetFn + " === 'function') fn = " + targetFn + "; } catch(e) {}\n"
                    + "}\n"
                    + "if (!fn) {\n"
                    + "    for (const name of ['" + targetFn + "', 'solve', 'solution', 'main']) {\n"
                    + "        try { const c = eval(name); if (typeof c === 'function') { fn = c; break; } } catch(e) {}\n"
                    + "    }\n"
                    + "}\n"
                    + "if (fn) {\n"
                    + "    let parsed;\n"
                    + "    try {\n"
                    + "        parsed = JSON.parse(raw);\n"
                    + "    } catch(e) {\n"
                    + "        try { parsed = JSON.parse('[' + raw + ']'); } catch(e2) { parsed = raw; }\n"
                    + "    }\n"
                    + "\n"
                    + "    const p0Type = '" + p0 + "';\n"
                    + "    if (p0Type === 'string') {\n"
                    + "        if (Array.isArray(parsed) && parsed.length === 1 && typeof parsed[0] === 'string') {\n"
                    + "            parsed = parsed[0];\n"
                    + "        } else if (typeof parsed !== 'string') {\n"
                    + "            parsed = raw;\n"
                    + "        }\n"
                    + "    } else if (p0Type.includes('[]') || p0Type.includes('vector') || p0Type.includes('list')) {\n"
                    + "        if (!Array.isArray(parsed)) {\n"
                    + "            parsed = [parsed];\n"
                    + "        }\n"
                    + "    }\n"
                    + "\n"
                    + "    try {\n"
                    + "        let res;\n"
                    + "        if (fn.length === 1) {\n"
                    + "            try {\n"
                    + "                res = fn(parsed);\n"
                    + "            } catch(callErr) {\n"
                    + "                const spreadArgs = Array.isArray(parsed) ? parsed : [parsed];\n"
                    + "                res = fn(...spreadArgs);\n"
                    + "            }\n"
                    + "        } else if (fn.length > 1 && Array.isArray(parsed) && parsed.length === fn.length) {\n"
                    + "            res = fn(...parsed);\n"
                    + "        } else if (Array.isArray(parsed)) {\n"
                    + "            try {\n"
                    + "                res = fn(...parsed);\n"
                    + "            } catch(e) {\n"
                    + "                res = fn(parsed);\n"
                    + "            }\n"
                    + "        } else {\n"
                    + "            res = fn(parsed);\n"
                    + "        }\n"
                    + "        if (typeof res === 'boolean') {\n"
                    + "            console.log(res ? 'true' : 'false');\n"
                    + "        } else if (typeof res === 'object' && res !== null) {\n"
                    + "            console.log(JSON.stringify(res));\n"
                    + "        } else {\n"
                    + "            console.log(res);\n"
                    + "        }\n"
                    + "    } catch(err) {\n"
                    + "        console.error(err);\n"
                    + "        process.exit(1);\n"
                    + "    }\n"
                    + "}\n";
        }

        // ===================== JAVA =====================
        if (lang.equals("java")) {
            if (code.contains("public static void main") || code.contains("static void main")) {
                if (code.contains("public class") && !code.contains("public class Main")) {
                    return code.replaceFirst("public\\s+class\\s+", "class ") + "\n\n"
                            + "public class Main {\n"
                            + "    public static void main(String[] args) throws Exception {\n"
                            + "        Solution.main(args);\n"
                            + "    }\n"
                            + "}\n";
                }
                return code;
            }

            String targetFn = extractTargetFunctionName(code, question);
            String className = "Solution";
            Matcher cm = Pattern.compile("class\\s+([a-zA-Z0-9_$]+)").matcher(code);
            while (cm.find()) {
                String cName = cm.group(1);
                if (!cName.equals("Main")) {
                    className = cName;
                    break;
                }
            }
            String sanitizedCode = code.replaceAll("public\\s+class\\s+", "class ");

            return "import java.util.*;\nimport java.util.stream.*;\nimport java.lang.reflect.*;\n\n"
                    + sanitizedCode + "\n\n"
                    + "public class Main {\n"
                    + "    public static void main(String[] args) throws Exception {\n"
                    + "        java.util.Scanner sc = new java.util.Scanner(System.in);\n"
                    + "        java.lang.StringBuilder sb = new java.lang.StringBuilder();\n"
                    + "        while (sc.hasNextLine()) { sb.append(sc.nextLine()).append(\"\\n\"); }\n"
                    + "        String raw = sb.toString().trim();\n"
                    + "        Class<?> clazz = null;\n"
                    + "        try { clazz = Class.forName(\"" + className + "\"); } catch (Exception ignored) {}\n"
                    + "        if (clazz == null) {\n"
                    + "            try { clazz = Class.forName(\"Solution\"); } catch (Exception ignored) {}\n"
                    + "        }\n"
                    + "        if (clazz == null) {\n"
                    + "            System.err.println(\"Solution class not found\");\n"
                    + "            System.exit(1);\n"
                    + "        }\n"
                    + "        String targetName = \"" + targetFn + "\";\n"
                    + "        java.lang.reflect.Method targetMethod = null;\n"
                    + "        for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {\n"
                    + "            if (java.lang.reflect.Modifier.isPublic(m.getModifiers()) && !m.getName().startsWith(\"$\")) {\n"
                    + "                if (targetName.isEmpty() || m.getName().equalsIgnoreCase(targetName)) {\n"
                    + "                    targetMethod = m;\n"
                    + "                    break;\n"
                    + "                }\n"
                    + "            }\n"
                    + "        }\n"
                    + "        if (targetMethod == null) {\n"
                    + "            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {\n"
                    + "                if (java.lang.reflect.Modifier.isPublic(m.getModifiers()) && !m.getName().startsWith(\"$\")) {\n"
                    + "                    targetMethod = m;\n"
                    + "                    break;\n"
                    + "                }\n"
                    + "            }\n"
                    + "        }\n"
                    + "        if (targetMethod == null) {\n"
                    + "            System.err.println(\"Target method not found\");\n"
                    + "            System.exit(1);\n"
                    + "        }\n"
                    + "        Object instance = clazz.getDeclaredConstructor().newInstance();\n"
                    + "        Class<?>[] paramTypes = targetMethod.getParameterTypes();\n"
                    + "        Object[] invokedArgs = parseArgs(raw, paramTypes);\n"
                    + "        Object result = targetMethod.invoke(instance, invokedArgs);\n"
                    + "        printResult(result);\n"
                    + "    }\n"
                    + "    private static Object[] parseArgs(String raw, Class<?>[] types) {\n"
                    + "        if (types.length == 0) return new Object[0];\n"
                    + "        if (types.length == 1) {\n"
                    + "            String token = raw.trim();\n"
                    + "            if (!types[0].isArray() && !java.util.List.class.isAssignableFrom(types[0])) {\n"
                    + "                if (token.startsWith(\"[\") && token.endsWith(\"]\")) {\n"
                    + "                    token = token.substring(1, token.length() - 1).trim();\n"
                    + "                }\n"
                    + "            }\n"
                    + "            return new Object[]{ convertToken(token, types[0]) };\n"
                    + "        }\n"
                    + "        java.util.List<String> tokens = splitArgs(raw);\n"
                    + "        Object[] res = new Object[types.length];\n"
                    + "        for (int i = 0; i < types.length; i++) {\n"
                    + "            String token = i < tokens.size() ? tokens.get(i).trim() : \"\";\n"
                    + "            res[i] = convertToken(token, types[i]);\n"
                    + "        }\n"
                    + "        return res;\n"
                    + "    }\n"
                    + "    private static java.util.List<String> splitArgs(String raw) {\n"
                    + "        java.util.List<String> list = new java.util.ArrayList<>();\n"
                    + "        raw = raw.trim();\n"
                    + "        if (raw.startsWith(\"[\") && raw.endsWith(\"]\")) {\n"
                    + "            raw = raw.substring(1, raw.length() - 1).trim();\n"
                    + "        }\n"
                    + "        int depth = 0;\n"
                    + "        boolean inQuotes = false;\n"
                    + "        java.lang.StringBuilder cur = new java.lang.StringBuilder();\n"
                    + "        for (int i = 0; i < raw.length(); i++) {\n"
                    + "            char c = raw.charAt(i);\n"
                    + "            if (c == '\\\"' && (i == 0 || raw.charAt(i - 1) != '\\\\')) {\n"
                    + "                inQuotes = !inQuotes;\n"
                    + "                cur.append(c);\n"
                    + "            } else if (!inQuotes && (c == '[' || c == '{')) {\n"
                    + "                depth++;\n"
                    + "                cur.append(c);\n"
                    + "            } else if (!inQuotes && (c == ']' || c == '}')) {\n"
                    + "                depth--;\n"
                    + "                cur.append(c);\n"
                    + "            } else if (!inQuotes && depth == 0 && c == ',') {\n"
                    + "                list.add(cur.toString().trim());\n"
                    + "                cur = new java.lang.StringBuilder();\n"
                    + "            } else {\n"
                    + "                cur.append(c);\n"
                    + "            }\n"
                    + "        }\n"
                    + "        if (cur.length() > 0) list.add(cur.toString().trim());\n"
                    + "        return list;\n"
                    + "    }\n"
                    + "    private static Object convertToken(String token, Class<?> type) {\n"
                    + "        if (token.startsWith(\"\\\"\") && token.endsWith(\"\\\"\") && token.length() >= 2) {\n"
                    + "            token = token.substring(1, token.length() - 1);\n"
                    + "        }\n"
                    + "        if (type == String.class) {\n"
                    + "            return token;\n"
                    + "        } else if (type == int.class || type == Integer.class) {\n"
                    + "            return Integer.parseInt(token.trim().isEmpty() ? \"0\" : token.trim());\n"
                    + "        } else if (type == long.class || type == Long.class) {\n"
                    + "            return Long.parseLong(token.trim().isEmpty() ? \"0\" : token.trim());\n"
                    + "        } else if (type == double.class || type == Double.class) {\n"
                    + "            return Double.parseDouble(token.trim().isEmpty() ? \"0\" : token.trim());\n"
                    + "        } else if (type == boolean.class || type == Boolean.class) {\n"
                    + "            return Boolean.parseBoolean(token.trim());\n"
                    + "        } else if (type == char.class || type == Character.class) {\n"
                    + "            return token.isEmpty() ? ' ' : token.charAt(0);\n"
                    + "        } else if (type == int[].class) {\n"
                    + "            if (token.startsWith(\"[\") && token.endsWith(\"]\")) {\n"
                    + "                token = token.substring(1, token.length() - 1).trim();\n"
                    + "            }\n"
                    + "            if (token.isEmpty()) return new int[0];\n"
                    + "            String[] parts = token.split(\",\");\n"
                    + "            int[] arr = new int[parts.length];\n"
                    + "            for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());\n"
                    + "            return arr;\n"
                    + "        } else if (type == String[].class) {\n"
                    + "            if (token.startsWith(\"[\") && token.endsWith(\"]\")) {\n"
                    + "                token = token.substring(1, token.length() - 1).trim();\n"
                    + "            }\n"
                    + "            if (token.isEmpty()) return new String[0];\n"
                    + "            String[] parts = token.split(\",\");\n"
                    + "            String[] arr = new String[parts.length];\n"
                    + "            for (int i = 0; i < parts.length; i++) {\n"
                    + "                String s = parts[i].trim();\n"
                    + "                if (s.startsWith(\"\\\"\") && s.endsWith(\"\\\"\") && s.length() >= 2) s = s.substring(1, s.length() - 1);\n"
                    + "                arr[i] = s;\n"
                    + "            }\n"
                    + "            return arr;\n"
                    + "        } else if (java.util.List.class.isAssignableFrom(type)) {\n"
                    + "            if (token.startsWith(\"[\") && token.endsWith(\"]\")) {\n"
                    + "                token = token.substring(1, token.length() - 1).trim();\n"
                    + "            }\n"
                    + "            if (token.isEmpty()) return new java.util.ArrayList<>();\n"
                    + "            String[] parts = token.split(\",\");\n"
                    + "            java.util.List<Object> list = new java.util.ArrayList<>();\n"
                    + "            for (String part : parts) {\n"
                    + "                String p = part.trim();\n"
                    + "                try { list.add(Integer.parseInt(p)); } catch (Exception e) {\n"
                    + "                    if (p.startsWith(\"\\\"\") && p.endsWith(\"\\\"\") && p.length() >= 2) p = p.substring(1, p.length() - 1);\n"
                    + "                    list.add(p);\n"
                    + "                }\n"
                    + "            }\n"
                    + "            return list;\n"
                    + "        }\n"
                    + "        return token;\n"
                    + "    }\n"
                    + "    private static void printResult(Object result) {\n"
                    + "        if (result == null) {\n"
                    + "            System.out.println(\"null\");\n"
                    + "        } else if (result.getClass().isArray()) {\n"
                    + "            int len = java.lang.reflect.Array.getLength(result);\n"
                    + "            java.lang.StringBuilder sb = new java.lang.StringBuilder(\"[\");\n"
                    + "            for (int i = 0; i < len; i++) {\n"
                    + "                if (i > 0) sb.append(\",\");\n"
                    + "                Object elem = java.lang.reflect.Array.get(result, i);\n"
                    + "                if (elem != null && elem.getClass().isArray()) {\n"
                    + "                    int subLen = java.lang.reflect.Array.getLength(elem);\n"
                    + "                    sb.append(\"[\");\n"
                    + "                    for (int j = 0; j < subLen; j++) {\n"
                    + "                        if (j > 0) sb.append(\",\");\n"
                    + "                        sb.append(java.lang.reflect.Array.get(elem, j));\n"
                    + "                    }\n"
                    + "                    sb.append(\"]\");\n"
                    + "                } else {\n"
                    + "                    sb.append(elem);\n"
                    + "                }\n"
                    + "            }\n"
                    + "            sb.append(\"]\");\n"
                    + "            System.out.println(sb.toString());\n"
                    + "        } else if (result instanceof Boolean) {\n"
                    + "            System.out.println(((Boolean) result) ? \"true\" : \"false\");\n"
                    + "        } else {\n"
                    + "            System.out.println(result);\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n";
        }

        // ===================== C++ =====================
        if (lang.equals("cpp") || lang.equals("c++") || lang.equals("cc")) {
            if (code.matches("(?s).*\\bmain\\s*\\(.*")) {
                return "#include <iostream>\n#include <vector>\n#include <string>\n#include <sstream>\n#include <algorithm>\n#include <cctype>\n#include <map>\n#include <set>\n#include <cmath>\n\nusing namespace std;\n\n" + code;
            }
            String targetFn = extractTargetFunctionName(code, question);
            List<String> params = extractParamTypes(code, question);
            boolean isSolutionClass = code.contains("class Solution") || code.contains("struct Solution");

            StringBuilder cpp = new StringBuilder();
            cpp.append("""
#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <algorithm>
#include <cctype>
#include <map>
#include <set>
#include <queue>
#include <stack>
#include <cmath>

using namespace std;

#define private public
#define protected public
#define class struct

template<typename T>
ostream& operator<<(ostream& os, const vector<T>& v) {
    os << "[";
    for (size_t i = 0; i < v.size(); ++i) {
        os << v[i];
        if (i != v.size() - 1) os << ",";
    }
    os << "]";
    return os;
}

static inline string _cleanStr(string s) {
    while (!s.empty() && (s.back() == '\\r' || s.back() == '\\n' || s.back() == ' ')) s.pop_back();
    while (!s.empty() && (s.front() == '\\r' || s.front() == '\\n' || s.front() == ' ')) s.erase(s.begin());
    if (s.size() >= 2 && s.front() == '"' && s.back() == '"') s = s.substr(1, s.size() - 2);
    return s;
}

static inline int _parseInt(string s) {
    try { return stoi(_cleanStr(s)); } catch(...) { return 0; }
}

static inline vector<int> _parseVectorInt(string s) {
    vector<int> res;
    s = _cleanStr(s);
    if (s.size() >= 2 && s.front() == '[' && s.back() == ']') s = s.substr(1, s.size() - 2);
    stringstream ss(s);
    string item;
    while (getline(ss, item, ',')) {
        while (!item.empty() && isspace(item.front())) item.erase(item.begin());
        while (!item.empty() && isspace(item.back())) item.pop_back();
        if (!item.empty()) {
            try { res.push_back(stoi(item)); } catch(...) {}
        }
    }
    return res;
}

static inline vector<string> _parseVectorString(string s) {
    vector<string> res;
    s = _cleanStr(s);
    if (s.size() >= 2 && s.front() == '[' && s.back() == ']') s = s.substr(1, s.size() - 2);
    stringstream ss(s);
    string item;
    while (getline(ss, item, ',')) {
        item = _cleanStr(item);
        if (!item.empty()) res.push_back(item);
    }
    return res;
}

static inline vector<string> _splitArgs(string raw) {
    vector<string> list;
    while (!raw.empty() && (raw.back() == '\\r' || raw.back() == '\\n' || raw.back() == ' ')) raw.pop_back();
    while (!raw.empty() && (raw.front() == '\\r' || raw.front() == '\\n' || raw.front() == ' ')) raw.erase(raw.begin());
    if (raw.size() >= 2 && raw.front() == '[' && raw.back() == ']') {
        raw = raw.substr(1, raw.size() - 2);
    }
    int depth = 0;
    bool inQuotes = false;
    string cur = "";
    for (size_t i = 0; i < raw.size(); i++) {
        char c = raw[i];
        if (c == '"' && (i == 0 || raw[i - 1] != '\\\\')) {
            inQuotes = !inQuotes;
            cur += c;
        } else if (!inQuotes && (c == '[' || c == '{')) {
            depth++;
            cur += c;
        } else if (!inQuotes && (c == ']' || c == '}')) {
            depth--;
            cur += c;
        } else if (!inQuotes && depth == 0 && c == ',') {
            list.push_back(cur);
            cur = "";
        } else {
            cur += c;
        }
    }
    if (!cur.empty()) list.push_back(cur);
    return list;
}
""");

            cpp.append("\n// --- User Code ---\n");
            cpp.append(code).append("\n\n");
            cpp.append("""
int main() {
    string raw, line;
    while (getline(cin, line)) {
        if (!raw.empty()) raw += "\\n";
        raw += line;
    }
""");

            String caller = isSolutionClass ? "sol." + targetFn : targetFn;
            if (isSolutionClass) {
                cpp.append("    Solution sol;\n");
            }

            if (params.isEmpty() || params.size() == 1) {
                String p0 = params.isEmpty() ? "string" : params.get(0);
                if (p0.contains("[]") || p0.contains("vector")) {
                    cpp.append("    auto arg0 = _parseVectorInt(raw);\n");
                } else if (p0.equals("number") || p0.equals("int")) {
                    cpp.append("    auto arg0 = _parseInt(raw);\n");
                } else {
                    cpp.append("    auto arg0 = _cleanStr(raw);\n");
                }
                cpp.append("    auto res = ").append(caller).append("(arg0);\n");
            } else if (params.size() == 2) {
                cpp.append("    auto tokens = _splitArgs(raw);\n");
                String p0 = params.get(0);
                String p1 = params.get(1);
                if (p0.contains("[]") || p0.contains("vector")) {
                    cpp.append("    auto arg0 = tokens.size() > 0 ? _parseVectorInt(tokens[0]) : vector<int>();\n");
                } else if (p0.equals("number") || p0.equals("int")) {
                    cpp.append("    auto arg0 = tokens.size() > 0 ? _parseInt(tokens[0]) : 0;\n");
                } else {
                    cpp.append("    auto arg0 = tokens.size() > 0 ? _cleanStr(tokens[0]) : \"\";\n");
                }
                if (p1.contains("[]") || p1.contains("vector")) {
                    cpp.append("    auto arg1 = tokens.size() > 1 ? _parseVectorInt(tokens[1]) : vector<int>();\n");
                } else if (p1.equals("number") || p1.equals("int")) {
                    cpp.append("    auto arg1 = tokens.size() > 1 ? _parseInt(tokens[1]) : 0;\n");
                } else {
                    cpp.append("    auto arg1 = tokens.size() > 1 ? _cleanStr(tokens[1]) : \"\";\n");
                }
                cpp.append("    auto res = ").append(caller).append("(arg0, arg1);\n");
            } else {
                cpp.append("    auto tokens = _splitArgs(raw);\n");
                cpp.append("    auto arg0 = tokens.empty() ? \"\" : _cleanStr(tokens[0]);\n");
                cpp.append("    auto res = ").append(caller).append("(arg0);\n");
            }

            cpp.append("""
    cout << boolalpha << res << endl;
    return 0;
}
""");
            return cpp.toString();
        }

        // ===================== C =====================
        if (lang.equals("c")) {
            if (code.matches("(?s).*\\bmain\\s*\\(.*")) {
                return "#include <stdio.h>\n#include <stdlib.h>\n#include <string.h>\n#include <stdbool.h>\n#include <ctype.h>\n#include <math.h>\n\n" + code;
            }
            String targetFn = extractTargetFunctionName(code, question);
            List<String> params = extractParamTypes(code, question);

            return "#include <stdio.h>\n"
                    + "#include <stdlib.h>\n"
                    + "#include <string.h>\n"
                    + "#include <stdbool.h>\n"
                    + "#include <ctype.h>\n"
                    + "#include <math.h>\n\n"
                    + "// --- User Code ---\n"
                    + code + "\n\n"
                    + "int main() {\n"
                    + "    char buf[65536];\n"
                    + "    size_t len = fread(buf, 1, sizeof(buf) - 1, stdin);\n"
                    + "    buf[len] = '\\0';\n"
                    + "    while (len > 0 && (buf[len - 1] == '\\r' || buf[len - 1] == '\\n' || buf[len - 1] == ' ')) buf[--len] = '\\0';\n"
                    + "    char* p = buf;\n"
                    + "    while (*p == ' ' || *p == '\\r' || *p == '\\n') p++;\n"
                    + "    if (*p == '[' && *(p + 1) == '\"') {\n"
                    + "        p += 2;\n"
                    + "        char* end = strrchr(p, '\"');\n"
                    + "        if (end) *end = '\\0';\n"
                    + "    } else if (*p == '\"') {\n"
                    + "        p++;\n"
                    + "        char* end = strrchr(p, '\"');\n"
                    + "        if (end) *end = '\\0';\n"
                    + "    }\n"
                    + (params.size() == 1 && (params.get(0).equals("number") || params.get(0).equals("int"))
                        ? "    int res = " + targetFn + "(atoi(p));\n"
                        : "    int res = " + targetFn + "(p);\n")
                    + "    printf(\"%d\\n\", res);\n"
                    + "    return 0;\n"
                    + "}\n";
        }

        return code;
    }
}
