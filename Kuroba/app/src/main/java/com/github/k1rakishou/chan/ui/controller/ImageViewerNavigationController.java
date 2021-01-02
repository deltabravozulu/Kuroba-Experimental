/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.controller;

import android.content.Context;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.ui.NavigationControllerContainerLayout;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;

public class ImageViewerNavigationController
        extends ToolbarNavigationController {

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public ImageViewerNavigationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.controller_navigation_image_viewer);
        container = (NavigationControllerContainerLayout) view.findViewById(R.id.container);

        setToolbar(view.findViewById(R.id.toolbar));
        requireToolbar().setCallback(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        requireToolbar().removeCallback();
    }

    public void showImages(
            final List<ChanPostImage> images,
            final int index,
            final ChanDescriptor chanDescriptor,
            ImageViewerController.ImageViewerCallback imageViewerCallback
    ) {
        showImages(images, index, chanDescriptor, imageViewerCallback, null);
    }

    public void showImages(
            final List<ChanPostImage> images,
            final int index,
            final ChanDescriptor chanDescriptor,
            ImageViewerController.ImageViewerCallback imageViewerCallback,
            ImageViewerController.GoPostCallback goPostCallback
    ) {
        ImageViewerController imageViewerController = new ImageViewerController(
                chanDescriptor,
                context,
                requireToolbar()
        );

        imageViewerController.setGoPostCallback(goPostCallback);
        pushController(imageViewerController, false);
        imageViewerController.setImageViewerCallback(imageViewerCallback);
        imageViewerController.getPresenter().showImages(images, index, chanDescriptor);
    }
}
