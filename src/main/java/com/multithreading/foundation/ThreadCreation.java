package com.multithreading.foundation;

public class ThreadCreation {

    public static void main(String[] args) {

        Thread myThread = new MyThread();
        Thread myRunnable = new Thread(new MyRunnable());
        Thread myLambda = new Thread(() -> System.out.println("Implementing Lambda. Thread ID : " + Thread.currentThread().getId()));

        System.out.println("Main Thread ID : " + Thread.currentThread().getId());

        myThread.start();
        myRunnable.start();
        myLambda.start();
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

}
