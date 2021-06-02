package com.bignerdranch.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {
    private RecyclerView mPhotoRecyclerView;
    private static final String TAG = "PhotoGalleryFragment";
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // frees the fragment instance from the activity lifecycle.  Setting to
        // true retains the fragment across activity re-creation
        setRetainInstance(true);
        // starts AsyncTask which starts the background thread and calls doInBackground().
        updateItems();
        // turn on the menu
        setHasOptionsMenu(true);
        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder photoHolder, Bitmap bitmap) {
                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                        photoHolder.bindImageToView(drawable, null);
                    }
                }
        );
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.fragment_photo_gallery_menu, menu);
        // use this so we can get a reference to the SearchView
        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        //Grab it
        final SearchView searchView = (SearchView)searchItem.getActionView();
        // Set up the listener we need.
        searchView.setOnQueryTextListener(
            new SearchView.OnQueryTextListener() {
                // executes anytime the user submits a query
                // launches FetchItemsTask to query for new results
                @Override
                public boolean onQueryTextSubmit(String s) {
                    Log.d(TAG, "QueryTextSubmit: " + s);
                    updateItems();
                    // store the query in preferences.
                    QueryPreferences.setStoredQuery(getActivity(), s);
                    return true;    // signifies the query has been handled
                }

                // executes anytime text in SearchView changes
                @Override
                public boolean onQueryTextChange(String s) {
                    Log.d(TAG, "QueryTextChange: " + s);
                    return false;
                }
            }
        );

        searchView.setOnSearchClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String query = QueryPreferences.getStoredQuery(getActivity());
                    searchView.setQuery(query, false);
                }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                updateItems();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // wrapper for calling FetchItemTask.  Will be needed a lot later
    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // inflate the view, specify it's root as the ViewGroup argument, but
        // do not attach it
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = (RecyclerView)v.findViewById(R.id.photo_recycler_view);
        // don't forget, RecyclerView will crash without a layout manager.
        // 3 = the number of columns in the grid.
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        return v;
    }

    public void onDestroyView(){
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    private void setupAdapter() {
        if(isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem> > {
        private String mQuery;

        public FetchItemsTask(String query) {
            mQuery = query;
        }


        @Override
        protected List<GalleryItem> doInBackground(Void... params) {
            if(mQuery == null)
                return new FlickrFetchr().fetchRecentPhotos();
            else
                return new FlickrFetchr().searchPhotos(mQuery);
        }

        protected void onPostExecute(List<GalleryItem> items) {
            mItems = items;
            setupAdapter();
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;
        private TextView mInfoText;

        public PhotoHolder(View itemView)
        {
            super(itemView);
            mItemImageView = (ImageView)itemView.findViewById(R.id.item_image_view);
            mInfoText = (TextView)itemView.findViewById(R.id.image_info);
        }

        public void bindImageToView(Drawable drawable, GalleryItem item)
        {
            mItemImageView.setImageDrawable(drawable);
            if(item != null)
            {
                mInfoText.setText(item.toString());
            }
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup group, int viewType)
        {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.list_item_gallery, group, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            Drawable placeholder = getResources().getDrawable(R.drawable.sharks_logo);
            photoHolder.bindImageToView(placeholder, galleryItem);
            mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getmUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }
}

