package com.fivedoorsescape.world;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.fivedoorsescape.content.FlashlightBalance;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

/**
 * Aturdir animatronicos con destellos de la linterna (CLAUDE.md §2.58).
 *
 * Un "destello" es un ciclo real encendido -> apagado: mientras la linterna esta encendida se marca a cada
 * animatronico que el haz ilumina de verdad (dentro del alcance y del cono, con linea de vision libre segun las
 * colisiones del mapa: iluminar la pared detras de la que se esconde no cuenta); al APAGARLA, cada marcado suma UNO.
 * Mantenerla encendida mucho tiempo sigue siendo un solo ciclo. Los contadores son independientes y acumulativos (no
 * se pierden por dejar de iluminar); al llegar al umbral de flashlight.json el animatronico queda aturdido (AISystem
 * lo detiene, no puede atrapar y sacude la cabeza) y su contador vuelve a 0. Mientras esta aturdido no acumula, asi
 * que no se lo puede encadenar indefinidamente: tiene que volver a juntar todos los ciclos.
 */
public class AturdimientoLinterna {

    private final FlashlightBalance balance;
    private final CollisionWorld mundo;
    private final Sound sonido;
    private boolean encendidaAntes = true;
    private final Vector3 objetivo = new Vector3();
    private final Vector3 dir = new Vector3();

    public AturdimientoLinterna(FlashlightBalance balance, CollisionWorld mundo, Sound sonido) {
        this.balance = balance;
        this.mundo = mundo;
        this.sonido = sonido;
    }

    /** Configura umbral y duracion de cada guardia segun su id (los que no aparecen en el JSON quedan en 0). */
    public void configurar(Array<AIComponent> guardias) {
        for (AIComponent ai : guardias) {
            FlashlightBalance.Stun s = balance.stun.get(ai.id);
            ai.destellosParaAturdir = s != null ? s.cycles : 0;
            ai.duracionAturdimiento = s != null ? s.seconds : 0f;
        }
    }

    /** Llamar una vez por frame de juego, ANTES de engine.update (asi un aturdimiento gana a una atrapada del mismo
     * frame). Devuelve el guardia aturdido en este frame (o null), para la reaccion visual. */
    public AIComponent actualizar(Array<AIComponent> guardias, Array<Entity> entidades, Camera camara, boolean encendida) {
        AIComponent aturdido = null;
        if (encendida) {
            float cosMax = MathUtils.cosDeg(balance.beamHalfAngleDegrees);
            for (int i = 0; i < guardias.size; i++) {
                AIComponent ai = guardias.get(i);
                if (ai.destellosParaAturdir <= 0 || ai.aturdidoRestante > 0f || ai.iluminadoEnCiclo || ai.jugadorAtrapado) {
                    continue;
                }
                TransformComponent t = Mappers.transform.get(entidades.get(i));
                objetivo.set(t.position.x, t.position.y + balance.targetHeight, t.position.z);
                dir.set(objetivo).sub(camara.position);
                float d = dir.len();
                if (d > balance.beamRange || d < 0.01f) {
                    continue;
                }
                dir.scl(1f / d);
                if (dir.dot(camara.direction) < cosMax) {
                    continue;
                }
                if (mundo.lineaDeVisionLibre(camara.position, objetivo)) {
                    ai.iluminadoEnCiclo = true;
                }
            }
        } else if (encendidaAntes) {
            // se acaba de apagar: cierra el ciclo
            for (int i = 0; i < guardias.size; i++) {
                AIComponent ai = guardias.get(i);
                if (!ai.iluminadoEnCiclo) {
                    continue;
                }
                ai.iluminadoEnCiclo = false;
                if (ai.aturdidoRestante > 0f || ai.jugadorAtrapado) {
                    continue;
                }
                ai.destellos++;
                Gdx.app.log("Aturdimiento", ai.id + " destellos " + ai.destellos + "/" + ai.destellosParaAturdir);
                if (ai.destellos >= ai.destellosParaAturdir) {
                    aturdir(ai, entidades.get(i));
                    aturdido = ai;
                }
            }
        }
        encendidaAntes = encendida;
        return aturdido;
    }

    private void aturdir(AIComponent ai, Entity e) {
        ai.destellos = 0;
        ai.aturdidoRestante = ai.duracionAturdimiento;
        ai.yawAntesDeAturdir = Mappers.transform.get(e).yawDegrees;
        AnimationComponent an = Mappers.animation.get(e);
        if (an != null) {
            an.nombreLogicoActual = "idle";
        }
        if (sonido != null) {
            sonido.play(0.85f);
        }
        Gdx.app.log("Aturdimiento", ai.id + " ATURDIDO " + ai.duracionAturdimiento + " s");
    }

    /** Tras una atrapada todos vuelven a su sitio: contadores y aturdimientos a cero. */
    public void reiniciar(Array<AIComponent> guardias) {
        for (AIComponent ai : guardias) {
            ai.destellos = 0;
            ai.iluminadoEnCiclo = false;
            ai.aturdidoRestante = 0f;
        }
        encendidaAntes = true;
    }
}
