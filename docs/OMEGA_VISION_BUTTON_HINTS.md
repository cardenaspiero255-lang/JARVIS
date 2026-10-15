# Omega Vision — posibles botones de una imagen

## Implementación real
- Al elegir una imagen en el selector Android, el OCR local extrae texto y rectángulos de sus líneas.
- Un detector **heurístico** busca exclusivamente etiquetas de acciones predefinidas en español (`JUGAR`, `ACEPTAR`, `CANCELAR`, `CONTINUAR`, etc.).
- Valida límites y geometría; presenta hasta seis sugerencias con coordenadas **relativas a la imagen muestreada**.
- Descarta instrucciones adicionales y textos no reconocidos; no identifica con certeza controles interactivos: un texto igual puede aparecer en un cartel, imagen o documento.
- **No pulsa botones**, no habilita gestos, no necesita permisos de captura de pantalla ni modifica las políticas de Omega Hands.

## Qué sigue pendiente
La visión continua de la pantalla requiere un flujo MediaProjection consentido desde el sistema, indicador persistente, parada accesible, control de recursos y verificación física Android. Esta entrega no incorpora vigilancia continua.

## Pruebas
JVM: etiquetas exactas, límites, rechazo de prompt injection, geometría fuera de rango y máximo de sugerencias. Contrato Python: el módulo de visión no invoca el ejecutor Android. Ninguna prueba aislada verifica físicamente la presencia o capacidad de pulsar un botón.
