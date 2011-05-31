package com.radicaldynamic.groupinform.utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.radicaldynamic.groupinform.documents.FormDefinition;

public class DocumentUtils
{
    /*
     * Sort a list of form definition documents alphabetically by name
     */
    public static <T> void sortByName(ArrayList<T> documents) {        
        Collections.sort(documents, new Comparator<T>() {
            @Override
            public int compare(T o1, T o2)
            {
                FormDefinition f1 = (FormDefinition) o1;
                FormDefinition f2 = (FormDefinition) o2;
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        });    
    }
}
