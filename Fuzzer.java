import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        String seedInput = "<html a=\"value\">...</html>";

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        runCommand(builder, seedInput, getMutatedInputs(seedInput, List.of(     //All the String lengths are held relatively close to the error point so the Workflow can run
                input -> input.replace("<",""),   //The first Remove random Brackets
                input -> input.replace("</",""),
                input -> input.replace(">",""),
                Fuzzer::replaceOpenBraces,  //The open Braces get replaced by a random amount
                Fuzzer::replaceCloseBraces, //The closing Braces get replaced by a random amount
                Fuzzer::replaceTag,         //The Tags get replaced by a random String of random length
                Fuzzer::replaceContent,     //The content between two Tags gets replaced by a string of random length
                Fuzzer::replaceAttributeName,   //The Attribute Name gets replaced by a string of random length
                Fuzzer::replaceAttributeValue,  //The Attribute Value gets replaced by a string of random length
                Fuzzer::doubleTag,              //The Most outer Tag gets doubled
                Fuzzer::doubleRandomTag         //The Tags get replaced by a random Tag and the Most outer one gets doubled

        )));
        System.out.println("Program finished with exit Code 0");
        System.exit(0);
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("/bin/sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true); // redirect stderr to stdout
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(
                input -> {
                    System.out.println("Running with Input: "+ input);
                    try{
                        Process process = builder.start();
                        OutputStream streamToCommand = process.getOutputStream();
                        streamToCommand.write(input.getBytes());
                        streamToCommand.flush();
                        streamToCommand.close();
                        InputStream streamFromCommand = process.getInputStream();
                        String output = readStreamIntoString(streamFromCommand);
                        streamFromCommand.close();
                        if (output.isEmpty()) {
                            System.out.println("Not a valid HTML File\n");
                        } else {
                            System.out.println("Output: " + output);
                        }
                        if(process.waitFor() != 0) {
                            System.out.println("Program finished with exit Code "+process.exitValue());
                            System.exit(process.exitValue());
                        }
                    }
                    catch (Exception e){
                        System.out.println(e.toString());
                    }
                }
        );
    }
    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }

    private static List<String> getMutatedInputs(String seedInput, Collection<Function<String, String>> mutators) {
        return mutators.stream()
                .map(mutator -> mutator.apply(seedInput)) // Apply each mutator function
                .collect(Collectors.toList());
    }

    //Create a Random String of a certain maxLength
    private static String getRandomString(int maxLength){
        Random random = new Random();
        int length = random.nextInt(maxLength) + 1;
        return random.ints(length, 'a', 'a' + 26)
                .mapToObj(Character::toChars) // int to char
                .map(String::valueOf) // char to string
                .collect(Collectors.joining());
    }

    //Replace all Tags with a randomaized String
    private static String replaceTag(String input){
        String regexStart = "<\\w+";
        String regexEnd = "</\\w+>";
        String newTag = getRandomString(24);
        String newInput = input.replaceAll(regexStart, "<"+newTag);
        newInput = newInput.replaceAll(regexEnd, "</"+newTag+">");
        return newInput;
    }

    //Double the Most Outer Tag
    private static String doubleTag(String input){
        String mostOuterTag = "";
        for(int i = 0; i < input.length()-1; i++){
            if(input.charAt(i) == '<' || input.charAt(i+1) == '/'){
                for(int j = i+2; j < input.length(); j++){
                    if(input.charAt(j) =='>'){
                        mostOuterTag = input.substring(i+2, j);
                    }
                }
            }
        }
        return "<"+mostOuterTag+">"+input+"</"+mostOuterTag+">";
    }
    // Replace all Tags with a random String and then double the most outer one
    private static String doubleRandomTag(String input){
        return doubleTag(replaceTag(input));
    }

    //The content between two Tags gets replaced by a string of random length
    private static String replaceContent(String input){
        String regex = ">.+<";
        return input.replaceFirst(regex, ">"+getRandomString(100)+"<");

    }

    //The Attribute Name gets replaced by a string of random length
    private static String replaceAttributeName(String input){
        String regex = "\\s\\w+=\"";
        return input.replaceFirst(regex, " " + getRandomString(50)+"=\"");
    }

    //The Attribute Value gets replaced by a string of random length
    private static String replaceAttributeValue(String input){
        String regex = "=\"\\w+\"";
        return input.replaceFirst(regex, "=\"" + getRandomString(12)+"\"");
    }

    //The open Braces get replaced by a random amount
    public static String replaceOpenBraces(String input){
        return input.replace("<", "<".repeat((int)(Math.random()*2)+1));
    }
    //The Closing Braces get replaced by a random amount
    public static String replaceCloseBraces(String input){
        return input.replace(">", ">".repeat((int)(Math.random()*2)+1));
    }
}
