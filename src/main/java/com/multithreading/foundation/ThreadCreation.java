package com.multithreading.foundation;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class ThreadCreation {

    public static void main(String[] args) {

        Thread myThread = new MyThread();
        Thread myRunnable = new Thread(new MyRunnable());
        Thread myLambda = new Thread(() -> System.out.println("Implementing Lambda. Thread ID : " +
                Thread.currentThread().getId()));

        FutureTask<Integer> futureTask = new FutureTask<>(new MyCallable());
        Thread myFutureTask = new Thread(futureTask);

        System.out.println("Main Thread ID : " + Thread.currentThread().getId());

        myThread.start();
        myRunnable.start();
        myLambda.start();
        myFutureTask.start();
    }

    private static class MyThread extends Thread {

        @Override
        public void run() {
            System.out.println("Extending Thread Class. Thread ID : " + Thread.currentThread().getId());
        }

    }

    private static class MyRunnable implements Runnable {

        @Override
        public void run() {
            System.out.println("Implementing Runnable. Thread ID : " + Thread.currentThread().getId());
        }

    }

    private static class MyCallable implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            System.out.println("Implementing Callable. Thread ID : " + Thread.currentThread().getId());
            return 10;
        }
    }

}
