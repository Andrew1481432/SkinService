package net.xtrafrancyz.skinservice.repository;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.xtrafrancyz.skinservice.Config;
import net.xtrafrancyz.skinservice.SkinService;
import net.xtrafrancyz.skinservice.processor.Image;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * @author xtrafrancyz
 */
public class SkinRepository {
    private static final Logger LOG = LoggerFactory.getLogger(SkinRepository.class);
    
    private LoadingCache<String, ImageContainer> skins;
    private LoadingCache<String, ImageContainer> capes;
    
    private Access access;
    private Type type;
    
    private String skinPath;
    private String capePath;
    private Image defaultSkin;
    
    public SkinRepository(SkinService service) {
        Config.RepositoryConfig config = service.config.repository;
        
        access = Access.valueOf(config.access);
        type = Type.valueOf(config.type);
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
    
    public static BufferedImage getBuffImageFromByteRgb(InputStream inputStream) throws IOException {
        try (DataInputStream dis = new DataInputStream(inputStream)) {
            
            int size = 64;
            
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int r = dis.read();
                    int g = dis.read();
                    int b = dis.read();
                    int a = dis.read();
                    
                    int rgb = ((a & 0xFF) << 24) |
                        ((r & 0xFF) << 16) |
                        ((g & 0xFF) << 8)  |
                        ((b & 0xFF) << 0);
                    
                    image.setRGB(x, y, rgb);
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
            if (access == Access.FILE) {
                LOG.debug("Read image from file: {}", path);
                if (type == Type.BYTE) {
                    img = getBuffImageFromByteRgb(Files.newInputStream(Paths.get(path)));
                } else if (type == Type.COMMON) {
                    img = ImageIO.read(new File(path));
                }
            } else if (access == Access.URL) {
                LOG.debug("Read image from url: {}", path);
                URL url = new URL(path);
                if (type == Type.BYTE) {
                    InputStream istream;
                    try {
                        istream = url.openStream();
                    } catch (IOException e) {
                        LOG.error("Can't get input stream from URL: {}", path);
                        return null;
                    }
                    
                    img = getBuffImageFromByteRgb(istream);
                } else if (type == Type.COMMON) {
                    img = ImageIO.read(url);
                }
            }
        } catch (Exception ignored) {}
        
        if (img == null) {
            LOG.error("Can't get image: {}", path);
            return null;
        } else
            return new Image(img);
    }
    
    private enum Type {
        BYTE,
        COMMON // Normal image format
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
