# Índice de Evidencias — Smart Campus Request Router

Carpeta del repositorio: `docs`

Esta carpeta corresponde a la sección **14. Evidencias de ejecución** del README del repositorio (`juandiegosc/smart-campus`), donde se referencia como "Evidencia adjunta en carpeta docs".

## Mapeo de capturas a evidencias requeridas (README, sección 14)

| # README | Evidencia requerida | Captura(s) del PDF |
|---|---|---|
| 1 | Contenedor RabbitMQ ejecutándose (`docker ps`) | Captura 1, Captura 2 |
| 2 | RabbitMQ Management UI — pantalla principal | Captura 3 |
| 3 | Exchange `campus.exchange` creado | Captura 5 |
| 4 | Listado de las seis colas creadas | Captura 4 |
| 5 | Mensaje publicado en `campus.requests.in` | Captura 8, Captura 9, Captura 18 |
| 6 | Mensaje transformado en `campus.admissions.queue` | Captura 10 |
| 7 | Mensaje transformado en `campus.payments.queue` | Captura 11 |
| 8 | Mensaje transformado en `campus.support.queue` | Captura 12 |
| 9 | Mensaje transformado en `campus.academic.queue` | Captura 13 |
| 10 | Mensaje no reconocido (`LIBRARY`) en `campus.manual-review.queue` | Captura 14 |
| 11 | Mensaje inválido (campos faltantes) en `campus.manual-review.queue` | Captura 15, Captura 19 |
| 12 | Logs de Apache Camel mostrando el flujo completo | Captura 16, Captura 17 |

## Evidencias adicionales (no listadas en la tabla del README, pero presentes en el PDF)

| Captura | Contenido |
|---|---|
| Captura 6 | Código `CanonicalRequestTranslator.java` (Message Translator) + build `BUILD SUCCESS` |
| Captura 7 | Código `CampusRequestRoute.java` (Content-Based Router) + arranque Spring Boot/Camel |
| Captura 18, 19 | Prueba de extensión: mensaje `SCHOLARSHIP` enrutado a `campus.manual-review.queue` (caso del Paso 15 del taller) |
