package com.oficinaagua.chatbot_api.ingesta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ChunkingService {

    private final ChunkingProperties props;

    public ChunkingService(ChunkingProperties props) {
        this.props = props;
    }

    /** Divide todos los .md de la carpeta configurada. */
    public List<Fragmento> dividirDirectorio() throws IOException {
        List<Fragmento> resultado = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(Path.of(props.ruta()))) {
            for (Path p : archivos.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                resultado.addAll(dividirDocumento(p.getFileName().toString(), Files.readString(p)));
            }
        }
        return resultado;
    }

    public List<Fragmento> dividirDocumento(String documento, String texto) {
        String limpio = texto.replace("\r\n", "\n");
        String titulo = documento;
        String seccionActual = null;
        StringBuilder cuerpo = new StringBuilder();
        List<Seccion> secciones = new ArrayList<>();

        for (String linea : limpio.split("\n", -1)) {
            if (linea.startsWith("## ")) {
                agregar(secciones, seccionActual, cuerpo);
                seccionActual = linea.substring(3).trim();
                cuerpo = new StringBuilder();
            } else if (linea.startsWith("# ") && seccionActual == null) {
                titulo = linea.substring(2).trim();
            } else {
                cuerpo.append(linea).append('\n');
            }
        }
        agregar(secciones, seccionActual, cuerpo);

        List<Fragmento> resultado = new ArrayList<>();
        int indice = 0;
        for (Seccion s : secciones) {
            String nombre = s.nombre() != null ? s.nombre() : titulo;
            for (String parte : partir(s.cuerpo())) {
                String contenido = titulo + " - " + nombre + "\n\n" + parte;
                resultado.add(new Fragmento(documento, nombre, indice++, contenido, sha256(contenido)));
            }
        }
        return resultado;
    }

    private void agregar(List<Seccion> secciones, String nombre, StringBuilder cuerpo) {
        String texto = cuerpo.toString().strip();
        if (!texto.isEmpty()) {
            secciones.add(new Seccion(nombre, texto));
        }
    }

    private List<String> partir(String texto) {
        int max = props.maxCaracteres();
        if (texto.length() <= max) {
            return List.of(texto);
        }
        List<String> partes = new ArrayList<>();
        int inicio = 0;
        while (inicio < texto.length()) {
            int fin = Math.min(inicio + max, texto.length());
            if (fin < texto.length()) {
                int corte = texto.lastIndexOf('\n', fin);
                if (corte <= inicio + max / 2) {
                    corte = texto.lastIndexOf(' ', fin);
                }
                if (corte > inicio + max / 2) {
                    fin = corte;
                }
            }
            String parte = texto.substring(inicio, fin).strip();
            if (!parte.isEmpty()) {
                partes.add(parte);
            }
            if (fin >= texto.length()) {
                break;
            }
            int nuevo = Math.max(fin - props.solapamiento(), inicio + 1);
            int espacio = texto.indexOf(' ', nuevo);
            inicio = (espacio != -1 && espacio < fin) ? espacio + 1 : nuevo;
        }
        return partes;
    }

    private static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Seccion(String nombre, String cuerpo) {}
}
