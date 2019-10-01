package com.minamax.bleserver;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

@SuppressWarnings("ALL")
public interface ValidationService {
    @GET
    Call<Integer> validate(@Query("key") String key);
}