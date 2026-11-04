package com.codingrank.assessment.service;

import com.codingrank.assessment.common.enums.SubmissionStatus;
import com.codingrank.assessment.dto.ExecutionResultDto;
import com.codingrank.assessment.model.Question;
import com.codingrank.assessment.model.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeJudgeServiceTest {

    private CodeJudgeService judgeService;
    private Question vowelQuestion;
    private List<TestCase> testCases;

    @BeforeEach
    void setUp() {
        judgeService = new CodeJudgeService();
        ReflectionTestUtils.setField(judgeService, "executionTimeoutMs", 5000L);

        vowelQuestion = Question.builder()
                .id("q_vowels_1")
                .title("Count Vowels in String")
                .functionSignature("countVowels(s: string): number")
                .points(100)
                .build();

        testCases = List.of(
                TestCase.builder()
                        .input("[\"AEIOU aeiou\"]")
                        .output("10")
                        .isHidden(false)
                        .build(),
                TestCase.builder()
                        .input("[\"hello world\"]")
                        .output("3")
                        .isHidden(true)
                        .build()
        );
    }

    @Test
    void testPythonEvaluation() {
        String code = """
                def countVowels(s: str) -> int:
                    count = 0
                    vowels = "aeiouAEIOU"
                    for char in s:
                        if char in vowels:
                            count += 1
                    return count
                """;

        ExecutionResultDto result = judgeService.evaluate(vowelQuestion, testCases, code, "python");
        assertEquals(SubmissionStatus.PASSED, result.getStatus());
        assertEquals(2, result.getTestCasesPassed());
        assertEquals(100, result.getScore());
    }

    @Test
    void testJavaScriptEvaluation() {
        String code = """
                function countVowels(s) {
                    let count = 0;
                    const vowels = "aeiouAEIOU";
                    for (let char of s) {
                        if (vowels.includes(char)) count++;
                    }
                    return count;
                }
                """;

        ExecutionResultDto result = judgeService.evaluate(vowelQuestion, testCases, code, "javascript");
        assertEquals(SubmissionStatus.PASSED, result.getStatus());
        assertEquals(2, result.getTestCasesPassed());
        assertEquals(100, result.getScore());
    }

    @Test
    void testJavaEvaluation() {
        String code = """
                class Solution {
                    public int countVowels(String s) {
                        int count = 0;
                        String vowels = "aeiouAEIOU";
                        for (char c : s.toCharArray()) {
                            if (vowels.indexOf(c) != -1) count++;
                        }
                        return count;
                    }
                }
                """;

        ExecutionResultDto result = judgeService.evaluate(vowelQuestion, testCases, code, "java");
        assertEquals(SubmissionStatus.PASSED, result.getStatus());
        assertEquals(2, result.getTestCasesPassed());
        assertEquals(100, result.getScore());
    }

    @Test
    void testCppEvaluation() {
        String code = """
                class Solution {
                public:
                    int countVowels(string s) {
                        int count = 0;
                        string v = "aeiouAEIOU";
                        for (char c : s) {
                            if (v.find(c) != string::npos) count++;
                        }
                        return count;
                    }
                };
                """;

        ExecutionResultDto result = judgeService.evaluate(vowelQuestion, testCases, code, "cpp");
        assertEquals(SubmissionStatus.PASSED, result.getStatus());
        assertEquals(2, result.getTestCasesPassed());
        assertEquals(100, result.getScore());
    }

    @Test
    void testCppEvaluationWithImplicitHeadersAndPrivateStaticMethod() {
        // User's exact submitted code: no headers, static method, no public: modifier, using tolower
        String code = """
                class Solution {
                    static int countVowels(string s) {
                        int count = 0;
                        string vowels = "aeiouAEIOU";
                        for (char ch : s) {
                            ch = tolower(ch);
                            if (vowels.find(ch) != string::npos) count++;
                        }
                        return count;
                    }
                };
                """;

        ExecutionResultDto result = judgeService.evaluate(vowelQuestion, testCases, code, "cpp");
        assertEquals(SubmissionStatus.PASSED, result.getStatus(), "C++ Output was: " + result.getCompilerOutput() + " / " + (result.getTestCaseDetails().isEmpty() ? "" : result.getTestCaseDetails().get(0).getActualOutput()));
        assertEquals(2, result.getTestCasesPassed());
        assertEquals(100, result.getScore());
    }

    @Test
    void testCEvaluation() {
        String code = """
                int countVowels(char* s) {
                    int count = 0;
                    char* v = "aeiouAEIOU";
                    for (int i = 0; s[i] != '\\0'; i++) {
                        if (strchr(v, s[i]) != NULL) count++;
                    }
                    return count;
                }
                """;

        ExecutionResultDto result = judgeService.evaluate(vowelQuestion, testCases, code, "c");
        assertEquals(SubmissionStatus.PASSED, result.getStatus());
        assertEquals(2, result.getTestCasesPassed());
        assertEquals(100, result.getScore());
    }

    @Test
    void testCompetitiveProgrammingCppWithMain() {
        Question cpQuestion = Question.builder()
                .id("q_cp_1")
                .title("Sum of Two Numbers")
                .points(100)
                .build();

        List<TestCase> cpTestCases = List.of(
                TestCase.builder().input("3 5").output("8").build(),
                TestCase.builder().input("10 20").output("30").build()
        );

        String cppCode = """
                int main() {
                    int a, b;
                    if (cin >> a >> b) {
                        cout << (a + b) << endl;
                    }
                    return 0;
                }
                """;

        ExecutionResultDto result = judgeService.evaluate(cpQuestion, cpTestCases, cppCode, "cpp");
        assertEquals(SubmissionStatus.PASSED, result.getStatus());
        assertEquals(2, result.getTestCasesPassed());
    }

    @Test
    void testFindMaxInArrayJavaScript() {
        Question maxQuestion = Question.builder()
                .id("6a773dfbe910bad0c87e20f5")
                .title("Find Maximum Value in Array")
                .functionSignature("int solve(int[] arr)")
                .points(100)
                .build();

        List<TestCase> maxTestCases = List.of(
                TestCase.builder().input("[-10, -3, -5]").output("-3").isHidden(false).build(),
                TestCase.builder().input("[3, 5, 2, 8, 1]").output("8").isHidden(false).build()
        );

        String jsCode = """
                function solve(arr) {
                    if (!arr || arr.length === 0) return 0;
                    return Math.max(...arr);
                }
                """;

        ExecutionResultDto result = judgeService.evaluate(maxQuestion, maxTestCases, jsCode, "javascript");
        assertEquals(SubmissionStatus.PASSED, result.getStatus(), "JS failed: " + result.getCompilerOutput() + " Details: " + result.getTestCaseDetails());
        assertEquals(2, result.getTestCasesPassed());
    }

    @Test
    void testFindMaxInArrayPython() {
        Question maxQuestion = Question.builder()
                .id("6a773dfbe910bad0c87e20f5")
                .title("Find Maximum Value in Array")
                .functionSignature("int solve(int[] arr)")
                .points(100)
                .build();

        List<TestCase> maxTestCases = List.of(
                TestCase.builder().input("[-10, -3, -5]").output("-3").isHidden(false).build(),
                TestCase.builder().input("[3, 5, 2, 8, 1]").output("8").isHidden(false).build()
        );

        String pyCode = """
                def solve(arr):
                    return max(arr)
                """;

        ExecutionResultDto result = judgeService.evaluate(maxQuestion, maxTestCases, pyCode, "python");
        assertEquals(SubmissionStatus.PASSED, result.getStatus(), "Python failed: " + result.getCompilerOutput() + " Details: " + result.getTestCaseDetails());
        assertEquals(2, result.getTestCasesPassed());
    }

    @Test
    void testFindMaxInArrayJava() {
        Question maxQuestion = Question.builder()
                .id("6a773dfbe910bad0c87e20f5")
                .title("Find Maximum Value in Array")
                .functionSignature("int solve(int[] arr)")
                .points(100)
                .build();

        List<TestCase> maxTestCases = List.of(
                TestCase.builder().input("[-10, -3, -5]").output("-3").isHidden(false).build(),
                TestCase.builder().input("[3, 5, 2, 8, 1]").output("8").isHidden(false).build()
        );

        String javaCode = """
                class Solution {
                    public int solve(int[] arr) {
                        int max = arr[0];
                        for (int v : arr) if (v > max) max = v;
                        return max;
                    }
                }
                """;

        ExecutionResultDto result = judgeService.evaluate(maxQuestion, maxTestCases, javaCode, "java");
        assertEquals(SubmissionStatus.PASSED, result.getStatus(), "Java failed: " + result.getCompilerOutput() + " Details: " + result.getTestCaseDetails());
        assertEquals(2, result.getTestCasesPassed());
    }

    @Test
    void testFindMaxInArrayCpp() {
        Question maxQuestion = Question.builder()
                .id("6a773dfbe910bad0c87e20f5")
                .title("Find Maximum Value in Array")
                .functionSignature("int solve(int[] arr)")
                .points(100)
                .build();

        List<TestCase> maxTestCases = List.of(
                TestCase.builder().input("[-10, -3, -5]").output("-3").isHidden(false).build(),
                TestCase.builder().input("[3, 5, 2, 8, 1]").output("8").isHidden(false).build()
        );

        String cppCode = """
                class Solution {
                public:
                    int solve(vector<int> arr) {
                        int max = arr[0];
                        for (int v : arr) if (v > max) max = v;
                        return max;
                    }
                };
                """;

        ExecutionResultDto result = judgeService.evaluate(maxQuestion, maxTestCases, cppCode, "cpp");
        assertEquals(SubmissionStatus.PASSED, result.getStatus(), "Cpp failed: " + result.getCompilerOutput() + " Details: " + result.getTestCaseDetails());
        assertEquals(2, result.getTestCasesPassed());
    }

    @Test
    void testArrayWhitespaceEquivalence() {
        Question q = Question.builder().id("q_arr").title("Two Sum").build();
        List<TestCase> tcs = List.of(
                TestCase.builder().input("dummy").output("[0, 1]").build()
        );
        // JS outputting [0,1] without space
        String jsCode = """
                function solve() {
                    return [0, 1];
                }
                """;
        ExecutionResultDto result = judgeService.evaluate(q, tcs, jsCode, "javascript");
        assertEquals(SubmissionStatus.PASSED, result.getStatus());
    }

    private void assertPasses(String signature, String code, String language, String input, String output) {
        Question question = Question.builder()
                .id("q_args")
                .title("Argument handling")
                .functionSignature(signature)
                .points(100)
                .build();
        List<TestCase> cases = List.of(TestCase.builder().input(input).output(output).isHidden(false).build());

        ExecutionResultDto result = judgeService.evaluate(question, cases, code, language);

        assertEquals(SubmissionStatus.PASSED, result.getStatus(),
                () -> signature + " on " + language + " gave " + result.getTestCaseDetails());
    }

    @Test
    void testJavaScriptWithUntypedSignatureGetsSeparateArguments() {
        assertPasses("function findRemainder(totalItems, centersCount)",
                "function findRemainder(totalItems, centersCount) {\n    return totalItems % centersCount;\n}",
                "javascript", "10, 3", "1");
        assertPasses("function calculateFullBatches(totalItems, boxCapacity)",
                "function calculateFullBatches(totalItems, boxCapacity) {\n    return Number(Math.floor(totalItems / boxCapacity));\n}",
                "javascript", "27, 5", "5");
        assertPasses("function computeSquaredDistance(coord1, coord2)",
                "function computeSquaredDistance(coord1, coord2) {\n    return Math.pow(coord1 - coord2, 2);\n}",
                "javascript", "5, 2", "9");
        assertPasses("function calculateTotalCost(prices, quantities)",
                "function calculateTotalCost(prices, quantities) {\n    let total = 0;\n    for (let i = 0; i < prices.length; i++) {\n        total += prices[i] * quantities[i];\n    }\n    return total;\n}",
                "javascript", "[10,20], [2,3]", "80");
    }

    @Test
    void testJavaScriptRenamedFunctionStillRuns() {
        assertPasses("function trackEnergy(baseEnergy, pulses)",
                "function calculateFinalEnergy(baseEnergy, pulses) {\n    let energy = baseEnergy;\n    for (let pulse of pulses) {\n        energy += pulse;\n    }\n    return energy;\n}",
                "javascript", "100, [10,-5,20]", "125");
        assertPasses("function trackEnergy(baseEnergy, pulses)",
                "const trackEnergy = (baseEnergy, pulses) => pulses.reduce((sum, pulse) => sum + pulse, baseEnergy);",
                "javascript", "50, []", "50");
    }

    @Test
    void testJavaScriptSingleUntypedParameter() {
        String reverse = "function reverse(s) {\n    return s.split('').reverse().join('');\n}";
        assertPasses("function reverse(s)", reverse, "javascript", "[\"abc\"]", "cba");
        assertPasses("function reverse(s)", reverse, "javascript", "\"abc\"", "cba");
        assertPasses("function sum(nums)",
                "function sum(nums) {\n    return nums.reduce((a, b) => a + b, 0);\n}",
                "javascript", "[1,2,3]", "6");
        assertPasses("function double(n)", "function double(n) {\n    return n + n;\n}", "javascript", "4", "8");
        assertPasses("function count(words)",
                "function count(words) {\n    return words.length;\n}",
                "javascript", "[\"a\",\"b\"]", "2");
    }

    @Test
    void testPythonWithUntypedSignatureGetsSeparateArguments() {
        assertPasses("function findRemainder(totalItems, centersCount)",
                "def findRemainder(totalItems, centersCount):\n    return totalItems % centersCount\n",
                "python", "10, 3", "1");
        assertPasses("function calculateTotalCost(prices, quantities)",
                "def calculateTotalCost(prices, quantities):\n    return sum(p * q for p, q in zip(prices, quantities))\n",
                "python", "[10,20], [2,3]", "80");
        assertPasses("function trackEnergy(baseEnergy, pulses)",
                "def calculate_final_energy(base_energy, pulses):\n    return base_energy + sum(pulses)\n",
                "python", "100, [10,-5,20]", "125");
        assertPasses("function reverse(s)", "def reverse(s):\n    return s[::-1]\n", "python", "[\"abc\"]", "cba");
    }

    @Test
    void testCppTypesComeFromCodeWhenSignatureIsUntyped() {
        assertPasses("function findRemainder(totalItems, centersCount)",
                "int findRemainder(int totalItems, int centersCount) {\n    return totalItems % centersCount;\n}",
                "cpp", "10, 3", "1");
        assertPasses("function trackEnergy(baseEnergy, pulses)",
                "int calculateFinalEnergy(int baseEnergy, vector<int> pulses) {\n    for (int pulse : pulses) baseEnergy += pulse;\n    return baseEnergy;\n}",
                "cpp", "100, [10,-5,20]", "125");
    }

    @Test
    void testCppTwoArrayArguments() {
        assertPasses("function calculateTotalCost(prices, quantities)",
                "int calculateTotalCost(vector<int> prices, vector<int> quantities) {\n    int total = 0;\n    for (size_t i = 0; i < prices.size(); i++) total += prices[i] * quantities[i];\n    return total;\n}",
                "cpp", "[10,20], [2,3]", "80");
    }

    @Test
    void testCWithTwoNumbers() {
        assertPasses("function findRemainder(totalItems, centersCount)",
                "int findRemainder(int totalItems, int centersCount) {\n    return totalItems % centersCount;\n}",
                "c", "10, 3", "1");
    }

    @Test
    void testJavaWithUntypedSignature() {
        assertPasses("function findRemainder(totalItems, centersCount)",
                "class Solution {\n    public int findRemainder(int totalItems, int centersCount) {\n        return totalItems % centersCount;\n    }\n}",
                "java", "10, 3", "1");
        assertPasses("function calculateTotalCost(prices, quantities)",
                "class Solution {\n    public int calculateTotalCost(int[] prices, int[] quantities) {\n        int total = 0;\n        for (int i = 0; i < prices.length; i++) total += prices[i] * quantities[i];\n        return total;\n    }\n}",
                "java", "[10,20], [2,3]", "80");
    }

    @Test
    void testTypedParameterNamesAreNotMistakenForNumbers() {
        assertPasses("string shout(string points)",
                "string shout(string points) {\n    return points + \"!\";\n}",
                "cpp", "\"hi\"", "hi!");
    }
}
