package com.febi.rxjavaretrofitsample.datastructure;

import com.google.gson.annotations.SerializedName;

public class GithubIssue {
    String id;
    String title;
    public String comments_url;

    @SerializedName("body")
    public String comment;

    @Override
    public String toString() {
        return id + " - " + title;
    }
}
