package server.model;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import common.MsgType;

/**
 *
 * @author yuchen
 */
public class Game {
    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
    private boolean gameRound = false;
    public String word;
    public String currentState = "";
    public char[] letterArray;
    public char[] dashes;
    private boolean first = true;
    int remainingGuesses = 0;
    public int score = 0;

    public void appendEntry(MsgType cmd, String msg) {
        entries.add(cmd.toString() + "##" + msg);
    }

    public String[] getGameStatus() {
        return entries.toArray(new String[0]);
    }

    public void selectedWord() throws IOException {
        first = true;
        Path path = Paths.get("C:\\Users\\Ramiz\\Documents\\NetBeansProjects\\Hangman\\src\\resources\\words.txt");
        Stream<String> lines = Files.lines(path);
        long numberOfLines = lines.count();
        word = "";
        Random ran = new Random();
        int wordLine = ran.nextInt((int) numberOfLines);
        System.out.println("word line= " + wordLine);
        word = Files.lines(path).skip(wordLine - 1).findFirst().get().toUpperCase();
        letterArray = word.toCharArray();
        remainingGuesses = word.length();
        System.out.println("word = " + word);
        System.out.println();
        showCurrentState();
    }

    public void emptyWord() {
        first = false;
        dashes = new char[letterArray.length];
        for (int i = 0; i < dashes.length; i++)
            dashes[i] = '_';
    }

    public void playGame(String guess) {
        guess = guess.toUpperCase();
        if (guess.length() == 1) {
            if (checkAndUpdateLetter(letterArray, guess.charAt(0))) {
                System.out.println("Right letter");
            } else {
                System.out.println("Wrong letter, try again!");
                remainingGuesses--;
            }
        } else {
            if (guess.compareTo(word) == 0) {
                System.out.print("Win, the word is ");
                dashes = word.toCharArray();
            } else {
                System.out.println("Wrong word, try again!");
                remainingGuesses--;
            }
        }
    }
    
    public boolean correctWord() {
        String dash = new String(dashes);

        if(dash.toUpperCase().compareTo(word) == 0) {
            score++;
            return true;
        }
        else 
            return false;
    }
    
    public String showCurrentState() {
        currentState = "";
        if (first == true) emptyWord();
        
        for(int i=0; i<dashes.length; i++) {
            currentState = currentState + dashes[i] + " ";
        }
        return currentState;
    }
    
    public int remainingGuesses() {
        if(remainingGuesses == 0)
            score--;
        return remainingGuesses;
    }
        
    private boolean checkAndUpdateLetter (char[] letters, char letter) {
        boolean right = false;
        System.out.println(letter);
        for (int i = 0; i < letters.length; i++) {
            if (letters[i] == letter) {
               right = true;
               dashes[i] = letter;
            } 
        }
        return right;
    }
}
