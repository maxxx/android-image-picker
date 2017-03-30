package com.esafirm.imagepicker.features;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.esafirm.imagepicker.features.common.ImageLoaderListener;
import com.esafirm.imagepicker.model.Folder;
import com.esafirm.imagepicker.model.Image;

public class ImageLoader {

    private Context context;
    private ExecutorService executorService;

    public ImageLoader(Context context) {
        this.context = context;
    }

    private final String[] projection = new String[]{
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    };

    public void loadDeviceImages(final boolean isFolderMode, @Nullable String targetDirectory, final ImageLoaderListener listener) {
        getExecutorService().execute(new ImageLoadRunnable(isFolderMode, targetDirectory, listener));
    }

    public void abortLoadImages() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
    }

    private ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }

    private class ImageLoadRunnable implements Runnable {

        private String targetDirectory;
        private boolean isFolderMode;
        private ImageLoaderListener listener;

        public ImageLoadRunnable(boolean isFolderMode, @Nullable String targetDirectory, ImageLoaderListener listener) {
            this.isFolderMode = isFolderMode;
            this.listener = listener;
            this.targetDirectory = targetDirectory;
        }

        @Override
        public void run() {
            Cursor cursor;


            List<Image> temp = new ArrayList<>();
            Map<String, Folder> folderMap = null;
            if (isFolderMode) {
                folderMap = new HashMap<>();
            }

            if (!TextUtils.isEmpty(targetDirectory))
            {
                if (!isFolderMode)
                    return; // your mistake!
                ArrayList<Image> list = new ArrayList<>();
                parseAllImages(list, targetDirectory);
                String fName = new File(targetDirectory).getName();
                Folder folder = new Folder(fName);
                folderMap.put(fName, folder);
                folder.getImages().addAll(list);
            } else
            {
                cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                        null, null, MediaStore.Images.Media.DATE_ADDED);

                if (cursor == null) {
                    listener.onFailed(new NullPointerException());
                    return;
                }

                if (cursor.moveToLast()) {
                    do {
                        long id = cursor.getLong(cursor.getColumnIndex(projection[0]));
                        String name = cursor.getString(cursor.getColumnIndex(projection[1]));
                        String path = cursor.getString(cursor.getColumnIndex(projection[2]));
                        String bucket = cursor.getString(cursor.getColumnIndex(projection[3]));

                        File file = makeSafeFile(path);
                        if (file != null && file.exists()) {
                            Image image = new Image(id, name, path, false);
                            temp.add(image);

                            if (folderMap != null) {
                                Folder folder = folderMap.get(bucket);
                                if (folder == null) {
                                    folder = new Folder(bucket);
                                    folderMap.put(bucket, folder);
                                }
                                folder.getImages().add(image);
                            }
                        }

                    } while (cursor.moveToPrevious());
                }
                cursor.close();
            }

            /* Convert HashMap to ArrayList if not null */
            List<Folder> folders = null;
            if (folderMap != null) {
                folders = new ArrayList<>(folderMap.values());
            }

            listener.onImageLoaded(temp, folders);
        }
    }

    private static File makeSafeFile(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return new File(path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private final String[] extensions = { "jpg", "png", "jpeg", "JPG", "PNG", "JPEG" };

    private void parseAllImages(ArrayList<Image> images, String rootFolder) {
        File file = new File(rootFolder);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        parseAllImages(images, f.getAbsolutePath());
                    } else {
                        for (int i = 0; i < extensions.length; i++) {
                            if (f.getAbsolutePath().endsWith(extensions[i])) {
                                long id = -1;
                                String name = f.getName();
                                String path = f.getPath();
                                Image image = new Image(id, name, path, false);
                                images.add(image);
                            }
                        }
                    }
                }
            }
        }

    }
}
