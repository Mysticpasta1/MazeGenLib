package com.mystic.mazegenlib.core.interfaces;

public interface IEdge<U, V>
{
    public IGraphNode<U, V> head();
    public IGraphNode<U, V> tail();
    public V data();
}
