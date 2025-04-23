package dev.qwrky.taubot.APIManager;




/*
Aight, you're probably wondering what all of these callback objects are about?

basically, I fucked up and wrote this with a synchronous http API, which blocked the thread and caused
performance issues when running.
so I strapped asynchronous functionality on top because I cba to rewrite everything.
essentially you need to pass an instance of this callback class to api calls
and implement the run() method it inherits from runnable.
I'm not sure how much you know about java, but the T here indicates a generic type
this type is specified in the signature of the API methods your calling.
 */

public abstract class Callback<T> implements Runnable {
    public T result;
    public Exception exception = null;

    public void setResult(T result) {
        this.result = result;
    }

    public void setException(Exception e) {
        this.exception = e;
    }

}
