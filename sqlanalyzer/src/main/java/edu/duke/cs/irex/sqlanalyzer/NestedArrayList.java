package edu.duke.cs.irex.sqlanalyzer;

import java.util.ArrayList;
import java.util.List;

public class NestedArrayList extends ArrayList<Object> {

    // Constructor for leaf elements
    @SuppressWarnings("unchecked")
    public NestedArrayList(Object data) {
        super();
        if (data instanceof List<?>) {
            this.addAll((List<Object>) data);
        } else {
            this.add(data);
        }
    }

    // Constructor for nested lists
    public NestedArrayList() {
        super();
    }

    public void addAsList(Object element) {
        NestedArrayList newElem = new NestedArrayList();
        newElem.add(element);
        this.add(newElem);
    }

    public NestedArrayList concat(NestedArrayList arg) {
        NestedArrayList newList = new NestedArrayList();
        newList.add(this);
        newList.add(arg);
        return newList;
    }

    public NestedArrayList merge(NestedArrayList arg) {
        NestedArrayList newList = new NestedArrayList();
        for (Object o : this) {
            newList.add(o);
        }
        for (Object o : arg) {
            newList.add(o);
        }
        return newList;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("("); // Start with an opening parenthesis

        for (int i = 0; i < this.size(); i++) {
            Object element = this.get(i);

            if (element instanceof NestedArrayList) {
                sb.append(element.toString()); // Recursively call toString
            } else {
                sb.append(element); // Append leaf element
            }
            if (i < this.size() - 1) {
                sb.append(", "); // Add separator between elements
            }
        }

        sb.append(")"); // End with a closing parenthesis
        return sb.toString();
    }

    // Function to flatten the list and return a comma-separated string
    public String toStringFlat() {
        List<Object> flatList = flatten();
        return flatList.toString().replaceAll("[\\[\\]]", ""); // Convert list to string and remove
                                                               // brackets
    }

    // Function to flatten the nested structure into a single list
    private List<Object> flatten() {
        List<Object> flatList = new ArrayList<>();
        for (Object element : this) {
            if (element instanceof NestedArrayList) {
                flatList.addAll(((NestedArrayList) element).flatten()); // Recursively flatten
            } else {
                flatList.add(element); // Add element directly
            }
        }
        return flatList;
    }

    public static void addOffset(NestedArrayList intList, int offset) throws AnalyzerException {
        for (int i = 0; i < intList.size(); i++) {
            Object element = intList.get(i);
            if (element instanceof Integer) {
                intList.set(i, (Integer) element + offset);
            } else if (element instanceof NestedArrayList) {
                addOffset((NestedArrayList) element, offset); // Recursive call for nested list
            } else {
                throw new AnalyzerException(
                        "NestedArrayList does not support offset operation except for Integer");
            }
        }
    }

}
