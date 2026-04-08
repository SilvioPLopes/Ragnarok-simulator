package [PACOTE_BASE].populator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;

/**
 * Converte os ícones .bmp extraídos do GRF para .png,
 * removendo a cor de transparência magenta (#FF00FF) usada pelo cliente RO.
 *
 * Input:  pasta com .bmp extraídos de data\texture\유저인터페이스\item\
 * Output: pasta static/assets/items/ do projeto Spring Boot
 */
public class SpriteConverter {

    public void convertBmpToPng(Path inputBmp, Path outputPng) throws IOException {
        BufferedImage bmp = ImageIO.read(inputBmp.toFile());
        if (bmp == null) {
            System.err.println("Não conseguiu ler: " + inputBmp);
            return;
        }

        BufferedImage png = new BufferedImage(
                bmp.getWidth(), bmp.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        for (int y = 0; y < bmp.getHeight(); y++) {
            for (int x = 0; x < bmp.getWidth(); x++) {
                Color pixel = new Color(bmp.getRGB(x, y));
                boolean isMagenta = pixel.getRed() == 255
                        && pixel.getGreen() == 0
                        && pixel.getBlue() == 255;
                png.setRGB(x, y, isMagenta ? 0x00000000 : pixel.getRGB());
            }
        }

        Files.createDirectories(outputPng.getParent());
        ImageIO.write(png, "PNG", outputPng.toFile());
    }

    public void convertAll(Path inputDir, Path outputDir) throws IOException {
        try (var stream = Files.walk(inputDir)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".bmp"))
                    .forEach(bmpPath -> {
                        try {
                            String fileName = bmpPath.getFileName().toString()
                                    .replaceAll("(?i)\\.bmp$", ".png");
                            convertBmpToPng(bmpPath, outputDir.resolve(fileName));
                            System.out.println("Convertido: " + fileName);
                        } catch (IOException e) {
                            System.err.println("Falhou: " + bmpPath + " — " + e.getMessage());
                        }
                    });
        }
    }
}