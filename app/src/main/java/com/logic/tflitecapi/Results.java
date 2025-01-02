package com.logic.tflitecapi;

import android.graphics.RectF;

import java.util.List;

public class Results {
    RectF boundingBoxes;
    List<Category> categories;
}

class Category{
    //String nombre;
    String label;
    float score;
}