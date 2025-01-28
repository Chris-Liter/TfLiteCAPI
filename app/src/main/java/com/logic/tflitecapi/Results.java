package com.logic.tflitecapi;

import android.graphics.RectF;

import java.util.List;

public class Results {
    RectF boundingBoxes;
    List<Category> categories;

    Results(RectF boundingBoxes, List<Category> categories){
        this.categories = categories;
        this.boundingBoxes = boundingBoxes;
    }
}

class Category{
    //String nombre;
    String label;
    float score;

    Category(String label, float score){
        this.label = label;
        this.score = score;
    }
}