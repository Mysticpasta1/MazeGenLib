package com.mystic.mazegenlib.core.interfaces;

import com.mystic.mazegenlib.core.roomstuff.LinkedList;

public interface ILinkedListNode<T>
{
    public ILinkedListNode<T> next();
    public ILinkedListNode<T> prev();
    public T data();
    public void setData(T data);
    public LinkedList<T> owner();
    public T remove();
}
