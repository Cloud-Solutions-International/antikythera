package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.List;

public class Loops {
    public static void main(String[] args) {
        Loops loops = new Loops();
        loops.forLoop();
    }

    private void forLoop() {
        for (int i = 0; i < 10; i++) {
            System.out.println(i);
        }
    }

    private void forLoopWithBreak() {
        for (int i = 0; i < 20; ++i) {
            if (i == 10) {
                break;
            }
            System.out.println(i);
        }
    }

    private String forLoopWithReturn() {
        for (int i = 0; i < 20; ++i) {
            if (i == 10) {
                return "Hello world";
            }
            System.out.println(i);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private void whileLoop() {
        int i = 0;
        while (i < 10) {
            System.out.println(i);
            i++;
        }
    }

    @SuppressWarnings("unused")
    private void whileLoopWithBreak() {
        int i = 0;
        while (i < 20) {
            System.out.println(i);
            i++;
            if(i == 10) {
                break;
            }
        }
    }

    @SuppressWarnings("unused")
    private void doWhileLoop() {
        int i = 0;
        do {
            System.out.println(i);
            i++;
        } while (i < 10);
    }

    @SuppressWarnings("unused")
    private void forEachLoop() {
        int[] numbers = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int number : numbers) {
            System.out.println(number);
        }
    }

    @SuppressWarnings("unused")
    private void forEachLoopWithBreak() {
        int[] numbers = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 10};
        for (int number : numbers) {
            if(number == 10) {
                break;
            }
            System.out.println(number);
        }
    }


    private void forEach() {
        List<Integer> numbers = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        numbers.forEach(System.out::println);
    }

    private void forEach2() {
        List<Integer> numbers = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        numbers.forEach(x -> System.out.println(x));
    }

    private void forEach3() {
        List<Integer> numbers = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        forEach4(numbers);
    }

    private void forEach4(List<Integer> numbers) {
        numbers.forEach(x -> System.out.println(x));
    }

    @SuppressWarnings("unused")
    private void forEach5() {
        List<Integer> numbers = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        numbers.forEach(x -> { System.out.println(x); });
    }

    @SuppressWarnings("unused")
    private void forEachLoopWithList() {
        List<Integer> numbers = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        for (Integer number : numbers) {
            System.out.println(number);
        }
    }
}
