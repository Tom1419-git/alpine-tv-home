package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aucune allocation dans la boucle de rendu : les methodes de dessin de la scene ne contiennent aucun {@code new}
 * (formes, degrades et tableaux de particules sont prepares avant). Le test lit les sources et echoue au premier
 * {@code new} trouve dans une methode de dessin ; les methodes de preparation ({@code prepare}, {@code advance}, la
 * construction) sont hors du dessin et ne sont pas concernees.
 */
public class NoAllocationInRenderLoopTest {
    private static final Pattern DRAW_METHOD = Pattern.compile(
            "(?m)^    (?:private |protected |static |public )*(?:void|boolean|float) (draw\\w*|onDraw|rain|snow|lightning|unitCircle|unitSquash|fadeAlpha|hidden)\\(");

    private static Path source(String name) {
        Path p = Paths.get("src/main/java/com/gathin/tvhome/" + name + ".java");
        return Files.exists(p) ? p : Paths.get("launcher/app/src/main/java/com/gathin/tvhome/" + name + ".java");
    }

    /** (nom de la methode, corps) de chaque methode de dessin d'un fichier. */
    private static List<String[]> drawMethods(String file) throws IOException {
        String text = new String(Files.readAllBytes(source(file)), StandardCharsets.UTF_8);
        List<String[]> out = new ArrayList<>();
        Matcher m = DRAW_METHOD.matcher(text);
        while (m.find()) {
            int open = text.indexOf('{', m.end());
            int depth = 0, i = open;
            do {
                char c = text.charAt(i++);
                if (c == '{') depth++;
                else if (c == '}') depth--;
            } while (depth > 0);
            out.add(new String[]{m.group(1), text.substring(open, i)});
        }
        return out;
    }

    private static void assertNoNew(String file, int minMethods) throws IOException {
        List<String[]> methods = drawMethods(file);
        assertTrue(file + " : methodes de dessin trouvees : " + methods.size(), methods.size() >= minMethods);
        for (String[] method : methods) {
            String body = method[1].replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
            Matcher m = Pattern.compile("\\bnew\\s+[A-Z]").matcher(body);
            assertEquals(file + "." + method[0] + " alloue dans la boucle de rendu", false, m.find());
        }
    }

    @Test
    public void leFondPhotoNeAllouePasPendantLeDessin() throws IOException {
        assertNoNew("PhotoBackdrop", 9);
    }

    @Test
    public void laMeteoDePremierPlanNeAllouePasPendantLeDessin() throws IOException {
        assertNoNew("WeatherLayer", 4);
    }

    @Test
    public void laVueNeAllouePasPendantLeDessin() throws IOException {
        assertNoNew("AlpineHomeView", 5);
    }

    @Test
    public void leModeleDeContrasteNeAllouePasDansLeCalculDesZones() throws IOException {
        String text = new String(Files.readAllBytes(source("SceneZones")), StandardCharsets.UTF_8);
        int a = text.indexOf("static void range(");
        int b = text.indexOf("private static void blend(");
        String body = text.substring(a, b);
        assertEquals("SceneZones.range alloue", false, Pattern.compile("\\bnew\\s+[A-Z]").matcher(body).find());
    }

    @Test
    public void lesObjetsRecreesLeSontSeulementDansPrepare() throws IOException {
        String text = new String(Files.readAllBytes(source("PhotoBackdrop")), StandardCharsets.UTF_8);
        int a = text.indexOf("public void prepare(Scene s)");
        int b = text.indexOf("private void buildSky");
        assertTrue(text.substring(a, b).contains("new ColorMatrixColorFilter"));
    }
}
