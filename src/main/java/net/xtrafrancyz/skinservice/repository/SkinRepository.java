package net.xtrafrancyz.skinservice.repository;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.xtrafrancyz.skinservice.Config;
import net.xtrafrancyz.skinservice.SkinService;
import net.xtrafrancyz.skinservice.processor.Image;

import javax.imageio.ImageIO;
import java.awt.Color;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * @author xtrafrancyz
 */
public class SkinRepository {
    private static final Logger LOG = LoggerFactory.getLogger(SkinRepository.class);
    
    private LoadingCache<String, ImageContainer> skins;
    private LoadingCache<String, ImageContainer> capes;
    
    private Access access;
    private String skinPath;
    private String capePath;
    private Image defaultSkin;
    
    public SkinRepository(SkinService service) {
        Config.RepositoryConfig config = service.config.repository;
        
        access = Access.valueOf(config.type);
        skinPath = config.skinPath;
        capePath = config.capePath;
        
        try {
            defaultSkin = new Image(ImageIO.read(getClass().getResourceAsStream("/char.png")));
        } catch (Exception ex) {
            throw new RuntimeException("Can't load default skin /char.png", ex);
        }
        
        skins = Caffeine.newBuilder()
            .weakValues()
            .expireAfterAccess(config.cacheExpireMinutes, TimeUnit.MINUTES)
            .build(username -> new ImageContainer(this.fetch(skinPath.replace("{username}", username))));
        
        capes = Caffeine.newBuilder()
            .weakValues()
            .expireAfterAccess(config.cacheExpireMinutes, TimeUnit.MINUTES)
            .build(username -> new ImageContainer(this.fetch(capePath.replace("{username}", username))));
        
        ImageIO.setUseCache(false);
    }
    
    public Image getSkin(String username, boolean orDefault) {
        ImageContainer img = skins.get(username);
        if (img.img == null && orDefault)
            return defaultSkin;
        else
            return img.img;
    }
    
    @SuppressWarnings("ConstantConditions")
    public Image getCape(String username) {
        return capes.get(username).img;
    }
    
    public void invalidateCape(String username) {
        LOG.trace("Cape of player {} purged", username);
        capes.invalidate(username);
    }
    
    public void invalidateSkin(String username) {
        LOG.trace("Skin of player {} purged", username);
        skins.invalidate(username);
    }
    
    public static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage img =
            new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int x, y;
        int ww = src.getWidth();
        int hh = src.getHeight();
        int[] ys = new int[h];
        for (y = 0; y < h; y++)
            ys[y] = y * hh / h;
        for (x = 0; x < w; x++) {
            int newX = x * ww / w;
            for (y = 0; y < h; y++) {
                int col = src.getRGB(newX, ys[y]);
                img.setRGB(x, y, col);
            }
        }
        return img;
    }
    
    public static BufferedImage fileToImage(String sourceFile) throws IOException {
        try {
            FileInputStream fis = new FileInputStream(sourceFile);
            //LOG.debug("fis lenght: " + fis.getChannel().size());
            DataInputStream dis = new DataInputStream(fis);
            int size = 64;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int red = dis.read();
                    int green = dis.read();
                    int blue = dis.read();
                    int alpha = dis.read();
                    //int argb = alpha << 24 + red << 16 + green << 8 + blue;
                    //int rgb = ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
                    image.setRGB(x, y, new Color(red, green, blue, alpha).getRGB()); // ??? FIXME аллокация нового обькта на каждый пиксель
                }
            }
        
            dis.close();
            return image;
        } catch (FileNotFoundException exception) {
            return null;
        }
    }
    
    private Image fetch(String path) {
        BufferedImage img = null;
        try {
            if (access == Access.URL) {
                LOG.debug("Read image from url: {}", path);
                img = ImageIO.read(new URL(path));
            } else if (access == Access.FILE) {
                LOG.debug("Read image from file: {}", path);
                img = ImageIO.read(new File(path));
                if(img == null) {
                    img = fileToImage(path);
                    if(img != null) {
                        LOG.debug("Image have rgba byte buffer");
                    }
                } else {
                    LOG.debug("Image have extension png");
                }
            }
        } catch (Exception ignored) {
            //LOG.error("Error read: ", ignored);
        }
        
        if (img == null) {
            LOG.error("image null");
            return null;
        } else
            return new Image(img);
    }
    
    private enum Access {
        URL,
        FILE
    }
    
    private static class ImageContainer {
        final Image img;
        
        public ImageContainer(Image img) {
            this.img = img;
        }
    }
}
