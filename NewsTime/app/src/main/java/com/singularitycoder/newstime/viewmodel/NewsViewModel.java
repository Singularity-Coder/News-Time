package com.singularitycoder.newstime.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.singularitycoder.newstime.helper.ApiIdlingResource;
import com.singularitycoder.newstime.helper.RequestStateMediator;
import com.singularitycoder.newstime.helper.UiState;
import com.singularitycoder.newstime.model.NewsItem;
import com.singularitycoder.newstime.repository.NewsRepository;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public final class NewsViewModel extends AndroidViewModel {

    @NonNull
    private final String TAG = "NewsViewModel";

    @NonNull
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @NonNull
    private NewsRepository newsRepository = NewsRepository.getInstance();

    @Nullable
    private LiveData<List<NewsItem.NewsArticle>> newsArticleList;

    public NewsViewModel(@NonNull Application application) {
        super(application);
        newsRepository = new NewsRepository(application);
        newsArticleList = newsRepository.getAllFromRoomDb();
    }

    // ROOM START______________________________________________________________

    public final void insertIntoRoomDbThroughRepository(NewsItem.NewsArticle newsArticle) {
        newsRepository.insertIntoRoomDb(newsArticle);
    }

    public final void updateInRoomDbThroughRepository(NewsItem.NewsArticle newsArticle) {
        newsRepository.updateInRoomDb(newsArticle);
    }

    public final void deleteFromRoomDbThroughRepository(NewsItem.NewsArticle newsArticle) {
        newsRepository.deleteFromRoomDb(newsArticle);
    }

    public final void deleteAllFromRoomDbThroughRepository() {
        newsRepository.deleteAllFromRoomDb();
    }

    public final LiveData<List<NewsItem.NewsArticle>> getAllFromRoomDbThroughRepository() {
        return newsArticleList;
    }

    // ROOM END______________________________________________________________

    public final LiveData<RequestStateMediator<Object, UiState, String, String>> getNewsFromRepository(
            @Nullable final String country,
            @NonNull final String category,
            @Nullable final ApiIdlingResource idlingResource) throws IllegalArgumentException {

        if (null != idlingResource) idlingResource.setIdleState(false);

        final RequestStateMediator<Object, UiState, String, String> requestStateMediator = new RequestStateMediator<>();
        final MutableLiveData<RequestStateMediator<Object, UiState, String, String>> mutableLiveData = new MutableLiveData<>();
        newsRepository = NewsRepository.getInstance();

        requestStateMediator.set(null, UiState.LOADING, "Loading...", null);
        mutableLiveData.postValue(requestStateMediator);

        compositeDisposable.add(
                newsRepository.getNewsFromApi(country, category)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableSingleObserver() {
                            @Override
                            public void onSuccess(Object o) {
                                Log.d(TAG, "onResponse: resp: " + o);
                                if (null != o) {
                                    requestStateMediator.set(o, UiState.SUCCESS, "Got Data!", "NEWS");
                                    mutableLiveData.postValue(requestStateMediator);
                                    if (null != idlingResource) idlingResource.setIdleState(true);
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                requestStateMediator.set(null, UiState.ERROR, e.getMessage(), null);
                                mutableLiveData.postValue(requestStateMediator);
                                if (null != idlingResource) idlingResource.setIdleState(true);
                            }
                        })
        );
        return mutableLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        compositeDisposable.dispose();
    }
}
