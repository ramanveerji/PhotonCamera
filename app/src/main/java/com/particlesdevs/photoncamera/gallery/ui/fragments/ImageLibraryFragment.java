package com.particlesdevs.photoncamera.gallery.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.databinding.FragmentGalleryImageLibraryBinding;
import com.particlesdevs.photoncamera.databinding.ThumbnailSquareImageViewBinding;
import com.particlesdevs.photoncamera.gallery.adapters.DragSelectionItemTouchListener;
import com.particlesdevs.photoncamera.gallery.adapters.ImageGridAdapter;
import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.gallery.helper.Constants;
import com.particlesdevs.photoncamera.gallery.interfaces.OnItemInteractionListener;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.gallery.viewmodel.GalleryViewModel;

import org.apache.commons.io.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class ImageLibraryFragment extends Fragment implements ImageGridAdapter.GridAdapterCallback {
    private static final String TAG = ImageViewerFragment.class.getSimpleName();
    private FragmentGalleryImageLibraryBinding fragmentGalleryImageLibraryBinding;
    private NavController navController;
    private ImageGridAdapter imageGridAdapter;
    private ImageGridAdapter linearGridAdapter;
    private RecyclerView recyclerView;
    private boolean isFABOpen;
    private List<GalleryItem> galleryItems;
    private GalleryViewModel viewModel;
    private RecyclerView linearRecyclerView;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentGalleryImageLibraryBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_gallery_image_library, container, false);
        navController = NavHostFragment.findNavController(this);
        navController.addOnDestinationChangedListener((navController, navDestination, bundle) -> onImageSelectionStopped());
        OnBackPressedCallback back = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (imageGridAdapter != null && !imageGridAdapter.getSelectedItems().isEmpty()) {
                    onImageSelectionStopped();
                } else {
                    navController.navigateUp();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), back);
        return fragmentGalleryImageLibraryBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(GalleryViewModel.class);
        linearRecyclerView = fragmentGalleryImageLibraryBinding.scrollingGalleryFolderView;
        recyclerView = fragmentGalleryImageLibraryBinding.imageGridRv;
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10); //trial
        recyclerView.addOnItemTouchListener(new DragSelectionItemTouchListener(recyclerView.getContext(), new OnItemInteractionListener() {
            @Override
            public void onItemClicked(RecyclerView view, RecyclerView.ViewHolder holder, int position) {
                //select(holder, position);
            }

            @Override
            public void onLongItemClicked(RecyclerView view, RecyclerView.ViewHolder holder, int position) {
                select(holder, position);
            }

            @Override
            public void onMultipleViewHoldersSelected(RecyclerView view, List<RecyclerView.ViewHolder> selections) {
            }

            @Override
            public void onViewHolderHovered(RecyclerView view, RecyclerView.ViewHolder holder) {
                select(holder, holder.getAbsoluteAdapterPosition());
            }

            private void select(RecyclerView.ViewHolder holder, int position) {
                ImageGridAdapter.GridItemViewHolder gridHolder = ((ImageGridAdapter.GridItemViewHolder) holder);
                ThumbnailSquareImageViewBinding binding = (ThumbnailSquareImageViewBinding) gridHolder.getBinding();
                imageGridAdapter.selectGalleryItem(binding.imageCard, galleryItems.get(position));
            }
        }));
        observeAllMediaFiles();
        viewModel.getUpdatePending().observe(getViewLifecycleOwner(),this::onUpdatePending);
        initListeners();
    }

    private void initLinearRecyclerAdapter(List<GalleryItem> galleryItems) {
        if (galleryItems != null) {
            ImageLibraryFragment frag = this;
            linearGridAdapter = new ImageGridAdapter(galleryItems, Constants.GALLERY_ITEM_TYPE_LINEAR_FOLDER);
            linearGridAdapter.setHasStableIds(true);
            linearGridAdapter.setGridAdapterCallback(new ImageGridAdapter.GridAdapterCallback() {
                @Override
                public void onItemClicked(int position, View view, GalleryItem galleryFolder) {
                    frag.onImageSelectionStopped();
                    viewModel.setCurrentFolderImages(galleryFolder);
                }

                @Override
                public void onImageSelectionChanged(int numOfSelectedFiles) {

                }

                @Override
                public void onImageSelectionStopped() {

                }
            });
            linearRecyclerView.setAdapter(linearGridAdapter);
        }
    }

    private void observeAllMediaFiles() {
        viewModel.getCurrentFolderImages().observe(getViewLifecycleOwner(), this::initImageAdapter);
        viewModel.getSelectedDisplayFolders().observe(getViewLifecycleOwner(), this::initLinearRecyclerAdapter);
    }

    private void onUpdatePending(Boolean pending) {
        if (pending) {
            viewModel.fetchAllMedia();
            viewModel.setCurrentFolderImages(viewModel.getAllSelectedImageFolder().getValue());
            viewModel.setUpdatePending(false);
        }
    }

    private void initImageAdapter(List<GalleryItem> galleryItems) {
        if (galleryItems != null) {
            this.galleryItems = galleryItems;
            imageGridAdapter = new ImageGridAdapter(this.galleryItems, Constants.GALLERY_ITEM_TYPE_GRID);
            imageGridAdapter.setHasStableIds(true);
            imageGridAdapter.setGridAdapterCallback(this);
            recyclerView.setAdapter(imageGridAdapter);
        }
    }

    private void initListeners() {
        fragmentGalleryImageLibraryBinding.numberFab.setOnLongClickListener(v -> {
            onImageSelectionStopped();
            return true;
        });
        fragmentGalleryImageLibraryBinding.setOnNumFabClicked(this::onNumFabClicked);
        fragmentGalleryImageLibraryBinding.setOnShareFabClicked(this::onShareFabClicked);
        fragmentGalleryImageLibraryBinding.setOnDeleteFabClicked(this::onDeleteFabClicked);
        fragmentGalleryImageLibraryBinding.setOnCompareFabClicked(this::onCompareFabClicked);
        fragmentGalleryImageLibraryBinding.setOnSettingsFabClicked(this::onSettingsFabClicked);
        fragmentGalleryImageLibraryBinding.settingsFab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (fragmentGalleryImageLibraryBinding.scrollingGalleryFolderView.getVisibility() == View.VISIBLE)
                    fragmentGalleryImageLibraryBinding.scrollingGalleryFolderView.setVisibility(View.GONE);
                else
                    fragmentGalleryImageLibraryBinding.scrollingGalleryFolderView.setVisibility(View.VISIBLE);
                return true;
            }
        });
    }

    private void onCompareFabClicked(View view) {
        List<GalleryItem> selectedItems = imageGridAdapter.getSelectedItems();
        if (selectedItems.size() == 2) {
            NavController navController = Navigation.findNavController(view);
            Bundle b = new Bundle(2);
            int image1pos = galleryItems.indexOf(selectedItems.get(0));
            int image2pos = galleryItems.indexOf(selectedItems.get(1));
            b.putInt(Constants.IMAGE1_KEY, image1pos);
            b.putInt(Constants.IMAGE2_KEY, image2pos);
            navController.navigate(R.id.action_imageLibraryFragment_to_imageCompareFragment, b);
        }
    }

    private void onDeleteFabClicked(View view) {
        List<GalleryItem> filesToDelete = imageGridAdapter.getSelectedItems();
        String numOfFiles = String.valueOf(filesToDelete.size());
        String totalFileSize = FileUtils.byteCountToDisplaySize((int) filesToDelete.stream().mapToLong(value -> value.getFile().getSize()).sum());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder
                .setMessage(getContext().getString(R.string.sure_delete_multiple, numOfFiles, totalFileSize))
                .setTitle(android.R.string.dialog_alert_title)
                .setIcon(R.drawable.ic_delete)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.yes, (dialog, which) -> GalleryFileOperations.deleteImageFiles(getActivity(), filesToDelete.stream().map(galleryItem -> (ImageFile) galleryItem.getFile()).collect(Collectors.toList()), this::handleImagesDeletedCallback))
                .create()
                .show();
    }

    private void onShareFabClicked(View view) {
        ArrayList<Uri> imageUris = (ArrayList<Uri>) imageGridAdapter.getSelectedItems().stream().map(galleryItem -> galleryItem.getFile().getFileUri()).collect(Collectors.toList());
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris);
        shareIntent.setType("image/*");
        startActivity(Intent.createChooser(shareIntent, null));
    }

    private void onNumFabClicked(View view) {
        if (!isFABOpen) {
            showFABMenu();
        } else {
            closeFABMenu();
        }
    }

    private void onSettingsFabClicked(View view) {
        NavController navController = Navigation.findNavController(view);
        navController.navigate(R.id.action_imageLibraryFragment_to_gallerySettingsFragment);
        imageGridAdapter.deselectAll();
    }

    private void showFABMenu() {
        isFABOpen = true;
        fragmentGalleryImageLibraryBinding.deleteFab.animate().translationY(-getResources().getDimension(R.dimen.standard_65));
        fragmentGalleryImageLibraryBinding.shareFab.animate().translationY(-getResources().getDimension(R.dimen.standard_125));
    }

    private void closeFABMenu() {
        isFABOpen = false;
        fragmentGalleryImageLibraryBinding.deleteFab.animate().translationY(0);
        fragmentGalleryImageLibraryBinding.shareFab.animate().translationY(0);
    }

    @Override
    public void onItemClicked(int position, View view, GalleryItem galleryItem) {
        Bundle b = new Bundle();
        b.putInt(Constants.IMAGE_POSITION_KEY, position);
        NavController navController = Navigation.findNavController(view);
        navController.navigate(R.id.action_imageLibraryFragment_to_imageViewerFragment, b);
//            navController.setGraph(navController.getGraph(), b);
    }

    @Override
    public void onImageSelectionChanged(int numOfSelectedFiles) {
        fragmentGalleryImageLibraryBinding.setButtonsVisible(true);
        fragmentGalleryImageLibraryBinding.setSelectedCount(String.valueOf(numOfSelectedFiles));
        fragmentGalleryImageLibraryBinding.setCompareVisible(numOfSelectedFiles == 2);
    }

    @Override
    public void onImageSelectionStopped() {
        if (imageGridAdapter != null) {
            imageGridAdapter.deselectAll();
            if (isFABOpen) {
                closeFABMenu();
            }
            if (fragmentGalleryImageLibraryBinding != null) {
                fragmentGalleryImageLibraryBinding.setButtonsVisible(false);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isFABOpen) {
            closeFABMenu();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getParentFragmentManager().beginTransaction().remove(ImageLibraryFragment.this).commitAllowingStateLoss();
        fragmentGalleryImageLibraryBinding = null;
    }

    public void handleImagesDeletedCallback(boolean isDeleted) {
        if (isDeleted) {
            List<GalleryItem> filesToDelete = imageGridAdapter.getSelectedItems();

            String numOfFiles = String.valueOf(filesToDelete.size());
            String totalFileSize = FileUtils.byteCountToDisplaySize((int) filesToDelete.stream().mapToLong(value -> value.getFile().getSize()).sum());
            galleryItems.removeAll(filesToDelete);
            if(!galleryItems.isEmpty()) {
                imageGridAdapter.setGalleryItemList(galleryItems);
                imageGridAdapter.notifyItemRangeChanged(0, imageGridAdapter.getItemCount());
            }
            onImageSelectionStopped();
            if(galleryItems.isEmpty()) {
                viewModel.setUpdatePending(true);
            }
            Snackbar.make(getView(),
                    getString(R.string.multiple_deleted_success, numOfFiles, totalFileSize),
                    Snackbar.LENGTH_SHORT).show();
        } else {
            Snackbar.make(getView(),
                    "Deletion Failed!",
                    Snackbar.LENGTH_SHORT).show();
        }
    }
}
