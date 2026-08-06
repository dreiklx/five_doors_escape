package com.fivedoorsescape.util;

import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.I18NBundle;
import com.fivedoorsescape.io.HandoffData;

// Unico punto de resolucion idioma -> texto para todo Five Doors Escape (pedido explicito del
// usuario 2026-08-05: "no quiero ningun texto escrito directamente en el codigo... todo debe
// salir desde los archivos de localizacion"), mismo comportamiento observable que el sistema de
// idioma de FDAF (un enum Idioma que decide que texto mostrar) pero con el contenido real de los
// textos viviendo en assets/i18n/*.properties en vez de ternarios repetidos por todo el codigo.
// strings.properties es el valor por defecto (espanol, igual que Idioma.ESPANOL es el default del
// lado Swing); strings_en.properties cubre el idioma ingles.
public final class Lang {

	private static I18NBundle bundle;
	private static HandoffData.Idioma idiomaCacheado;

	private Lang() {
	}

	public static String get(HandoffData.Idioma idioma, String clave) {
		asegurarBundle(idioma);
		return bundle.get(clave);
	}

	private static void asegurarBundle(HandoffData.Idioma idioma) {
		if (bundle != null && idioma == idiomaCacheado) {
			return;
		}
		Locale locale = idioma == HandoffData.Idioma.INGLES ? Locale.of("en") : Locale.of("es");
		bundle = I18NBundle.createBundle(Gdx.files.internal("i18n/strings"), locale, "UTF-8");
		idiomaCacheado = idioma;
	}
}
