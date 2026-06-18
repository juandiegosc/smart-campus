# Smart Campus Request Router

## Taller Semana 11 — Message Routing y Message Transformation con RabbitMQ y Apache Camel

**Asignatura:** Integración de Sistemas (ISWZ3104)  
**Docente:** Darío Villamarín G.  
**Universidad:** Universidad de las Américas 

---

## 1. Integrantes del grupo

| Rol | Integrante |
|-----|-----------|
| Arquitecto de integración | *Milton Dávila* |
| Desarrollador Camel | *Juan Diego Silva* |
| Responsable RabbitMQ | *Jimmy Albarracín* |
| Responsable de documentación | *Sebastián Largo* |

---

## 2. Descripción del problema de integración

Una institución educativa recibe solicitudes estudiantiles desde múltiples canales digitales: formulario web, aplicación móvil, plataforma administrativa e integraciones externas. Todas estas solicitudes convergen inicialmente en una única cola de entrada: `campus.requests.in`.

El problema tiene dos dimensiones:

1. **Heterogeneidad de formato:** los mensajes llegan en un formato externo (`snake_case`) que no coincide con el formato canónico interno requerido por los sistemas de la institución.
2. **Multiplicidad de destinos:** dependiendo del campo `request_type`, cada solicitud debe ser dirigida a una cola diferente para que el sistema correspondiente la procese.

La solución implementa dos patrones de Enterprise Integration Patterns (EIP):

| Patrón | Propósito |
|--------|-----------|
| **Message Translator** | Convierte el formato externo al formato canónico interno |
| **Content-Based Router** | Enruta el mensaje hacia la cola adecuada según el tipo de solicitud |

---

## 3. Diagrama del flujo

```
                    ┌──────────────────────────┐
  [Productores]     │    campus.requests.in    │
  Web / Mobile ───► │     (cola de entrada)    │
  Admin / Externo   └────────────┬─────────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │    Message Translator  │
                    │  CanonicalRequest      │
                    │   Translator.java      │
                    └────────────┬───────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │  Content-Based Router  │
                    │   CampusRequestRoute   │
                    └──┬───┬────┬────┬───┬──┘
                       │   │    │    │   │
          ADMISSION ───┘   │    │    │   └── INVALID / Desconocido
          PAYMENT  ────────┘    │    │
          SUPPORT  ─────────────┘    │
          ACADEMIC ──────────────────┘

  ┌─────────────────────┐   ┌──────────────────────┐
  │ campus.admissions   │   │  campus.payments      │
  │      .queue         │   │       .queue          │
  └─────────────────────┘   └──────────────────────┘
  ┌─────────────────────┐   ┌──────────────────────┐
  │  campus.support     │   │  campus.academic      │
  │      .queue         │   │       .queue          │
  └─────────────────────┘   └──────────────────────┘
                    ┌─────────────────────────┐
                    │  campus.manual-review   │
                    │       .queue            │
                    └─────────────────────────┘
```

---

## 4. Tecnologías utilizadas

| Tecnología | Versión | Rol |
|-----------|---------|-----|
| Java | 17+ | Lenguaje de implementación |
| Spring Boot | 3.3.5 | Framework base de la aplicación |
| Apache Camel | 4.8.1 | Motor de integración y enrutamiento |
| RabbitMQ | 3-management | Broker de mensajería |
| Docker | Última estable | Contenedorización de RabbitMQ |
| Maven | 3.x | Gestión de dependencias y build |
| Jackson | (vía Spring Boot) | Serialización/deserialización JSON |

---

## 5. Instrucciones para ejecutar RabbitMQ

Desde la raíz del proyecto, ejecutar:

```bash
docker compose up -d
```

Verificar que el contenedor esté activo:

```bash
docker ps
```

Acceder a la consola de administración desde el navegador:

```
URL:        http://localhost:15672
Usuario:    guest
Contraseña: guest
```

---

## 6. Instrucciones para configurar exchange, colas y bindings

Dar permisos de ejecución al script (solo la primera vez):

```bash
chmod +x scripts/setup-rabbitmq.sh
```

Ejecutar el script de configuración:

```bash
./scripts/setup-rabbitmq.sh
```

El script crea automáticamente:

- **Exchange:** `campus.exchange` (tipo `direct`, durable)
- **Colas:**
  - `campus.requests.in`
  - `campus.admissions.queue`
  - `campus.payments.queue`
  - `campus.support.queue`
  - `campus.academic.queue`
  - `campus.manual-review.queue`
- **Bindings:** cada cola se enlaza al exchange usando su propio nombre como routing key.

Verificar en RabbitMQ Management UI → pestaña **Queues and Streams** que las seis colas aparecen listadas.

---

## 7. Instrucciones para ejecutar la aplicación

Compilar el proyecto:

```bash
mvn clean package
```

Iniciar la aplicación:

```bash
mvn spring-boot:run
```

La aplicación quedará en escucha activa sobre `campus.requests.in`. En la consola aparecerán logs de Apache Camel indicando que la ruta fue registrada y está procesando mensajes.

> **Importante:** no cerrar esta terminal mientras se realizan las pruebas.

---

## 8. Instrucciones para publicar mensajes de prueba

### Script automático (requiere `jq`)

Instalar `jq` si no está disponible:

```bash
# Ubuntu/Debian
sudo apt install jq

# macOS
brew install jq
```

Dar permisos de ejecución y ejecutar:

```bash
chmod +x scripts/publish-messages.sh
./scripts/publish-messages.sh
```

El script publica seis mensajes: `ADMISSION`, `PAYMENT`, `SUPPORT`, `ACADEMIC`, un tipo no reconocido (`LIBRARY`) y un mensaje inválido con campos incompletos.


## 9. Tabla de reglas de enrutamiento

| Valor de `request_type` | Cola destino |
|------------------------|-------------|
| `ADMISSION` | `campus.admissions.queue` |
| `PAYMENT` | `campus.payments.queue` |
| `SUPPORT` | `campus.support.queue` |
| `ACADEMIC` | `campus.academic.queue` |
| Cualquier otro valor | `campus.manual-review.queue` |
| Mensaje inválido (campos faltantes) | `campus.manual-review.queue` |

---

## 10. Explicación del Message Translator

### ¿Qué hace?

El componente `CanonicalRequestTranslator.java` implementa la interfaz `Processor` de Apache Camel. Intercepta cada mensaje antes de que sea enrutado, lee el JSON en formato externo y produce un nuevo JSON en formato canónico interno.

### Transformación de campos

| Campo original (externo) | Campo canónico (interno) |
|--------------------------|--------------------------|
| `request_id` | `requestId` |
| `student_name` | `student.fullName` |
| `student_document` | `student.document` |
| `request_type` | `type` |
| `channel` | `sourceChannel` |
| `created_at` | `createdAt` |

### Validación

Antes de transformar, el componente verifica que todos los campos obligatorios estén presentes y no vacíos. Si alguno falta, produce un mensaje con `"status": "INVALID"` y lo dirige a la cola de revisión manual mediante la propiedad de intercambio `requestType = INVALID`.

### Ejemplo de transformación

**Entrada (formato externo):**

```json
{
  "request_id": "REQ-1001",
  "student_name": "Ana Pérez",
  "student_document": "1712345678",
  "request_type": "ADMISSION",
  "channel": "web",
  "created_at": "2026-06-10T10:30:00"
}
```

**Salida (formato canónico):**

```json
{
  "requestId": "REQ-1001",
  "student": {
    "fullName": "Ana Pérez",
    "document": "1712345678"
  },
  "type": "ADMISSION",
  "sourceChannel": "web",
  "createdAt": "2026-06-10T10:30:00"
}
```

---

## 11. Explicación del Content-Based Router

### ¿Qué hace?

La clase `CampusRequestRoute.java` extiende `RouteBuilder` de Apache Camel. Después de la transformación, lee la propiedad `requestType` establecida por el `CanonicalRequestTranslator` y decide, mediante un bloque `.choice()/.when()/.otherwise()`, a qué cola de RabbitMQ se debe enviar el mensaje.

### Fragmento clave

```java
.choice()
  .when(exchangeProperty("requestType").isEqualTo("ADMISSION"))
    .to("spring-rabbitmq:campus.exchange?routingKey=campus.admissions.queue")
  .when(exchangeProperty("requestType").isEqualTo("PAYMENT"))
    .to("spring-rabbitmq:campus.exchange?routingKey=campus.payments.queue")
  .when(exchangeProperty("requestType").isEqualTo("SUPPORT"))
    .to("spring-rabbitmq:campus.exchange?routingKey=campus.support.queue")
  .when(exchangeProperty("requestType").isEqualTo("ACADEMIC"))
    .to("spring-rabbitmq:campus.exchange?routingKey=campus.academic.queue")
  .otherwise()
    .to("spring-rabbitmq:campus.exchange?routingKey=campus.manual-review.queue")
.end();
```

El patrón garantiza que ningún productor necesita conocer la existencia de las colas destino. Toda la lógica de decisión vive en un único componente centralizado.

---

## 12. Explicación del modelo canónico

El **Canonical Data Model** es un formato de mensaje único acordado internamente que actúa como lingua franca entre todos los sistemas participantes en la integración. Independientemente del formato con el que llegue un mensaje desde el exterior, siempre se transforma a este modelo antes de circular por la arquitectura interna.

**Modelo canónico definido para este taller:**

```json
{
  "requestId": "string",
  "student": {
    "fullName": "string",
    "document": "string"
  },
  "type": "string",
  "sourceChannel": "string",
  "createdAt": "string (ISO 8601)"
}
```

**Beneficio principal:** si aparece un nuevo canal de entrada con un formato diferente, solo hay que crear un nuevo traductor que convierta ese formato al canónico. El router y los sistemas destino no necesitan ser modificados.

---

## 13. Actividad previa de investigación

### 13.1 Content-Based Router

**1. ¿Qué problema resuelve este patrón?**  
Resuelve el problema de tener que enviar mensajes a distintos destinos según el contenido del propio mensaje. Sin este patrón, el productor tendría que conocer todas las colas posibles y decidir por sí mismo a cuál enviar, generando alto acoplamiento.

**2. ¿Por qué es mejor que el productor no conozca todos los posibles destinos?**  
Porque así el productor cumple una única responsabilidad (generar y emitir el mensaje) y el router centraliza la lógica de decisión. Añadir o eliminar un destino implica modificar únicamente el router, sin tocar ningún productor.

**3. ¿Qué campo del mensaje se utilizará como criterio de decisión en este taller?**  
El campo `type` del mensaje canónico, que proviene del campo `request_type` en el mensaje original externo.

---

### 13.2 Message Translator

**1. ¿Qué problema resuelve este patrón?**  
Resuelve la incompatibilidad de formato entre sistemas heterogéneos. Cuando el sistema productor y el sistema consumidor usan estructuras de datos diferentes, el Message Translator convierte el mensaje de un formato al otro sin que ninguno de los dos deba adaptarse al otro.

**2. ¿Por qué dos sistemas pueden necesitar formatos distintos para representar la misma información?**  
Porque cada sistema fue desarrollado de manera independiente, con sus propias convenciones, tecnologías y equipos. Un formulario web puede generar JSON en `snake_case`, mientras que el sistema interno usa `camelCase` con estructura anidada. Forzar a ambos a usar el mismo formato crearía dependencias estrechas y dificultaría el mantenimiento independiente.

**3. ¿Qué transformación concreta se realizará en este taller?**  
Se convierte un JSON plano en `snake_case` (campos como `student_name`, `request_id`) a un JSON en `camelCase` con estructura anidada (objeto `student` con `fullName` y `document`), usando el componente `CanonicalRequestTranslator`.

---

### 13.3 Canonical Data Model

**1. ¿Qué es un modelo canónico?**  
Es un esquema de mensaje único definido por la organización que sirve de formato de referencia para todos los sistemas que participan en la integración. Actúa como un contrato neutral al que todos los mensajes deben ser traducidos antes de procesarse internamente.

**2. ¿Por qué puede reducir el acoplamiento entre sistemas?**  
Porque elimina las dependencias directas entre los formatos de los sistemas. Cada sistema solo necesita saber cómo traducir su propio formato al canónico y viceversa, en lugar de conocer el formato de todos los demás sistemas. Con N sistemas, se necesitan N traductores en lugar de N×(N-1) conversiones directas punto a punto.

**3. ¿Cuál es el modelo canónico definido para este taller?**  
```json
{
  "requestId": "string",
  "student": { "fullName": "string", "document": "string" },
  "type": "string",
  "sourceChannel": "string",
  "createdAt": "string"
}
```

---

### 13.4 RabbitMQ

**1. ¿Qué es una cola en RabbitMQ?**  
Es un buffer ordenado (FIFO) que almacena mensajes hasta que un consumidor los procesa. Cada mensaje persiste en la cola hasta ser confirmado (acknowledged) por el consumidor.

**2. ¿Qué diferencia existe entre exchange, queue y routing key?**  
- **Exchange:** punto de entrada de los mensajes. Recibe los mensajes de los productores y los distribuye a las colas según reglas de enrutamiento.
- **Queue:** almacén temporal donde esperan los mensajes hasta ser consumidos por una aplicación.
- **Routing key:** etiqueta asociada al mensaje que el exchange usa para decidir a cuál o cuáles colas entregarlo, dependiendo del tipo de exchange (direct, topic, fanout, headers).

**3. ¿Cómo se puede verificar que un mensaje llegó correctamente a una cola?**  
Accediendo a RabbitMQ Management UI en `http://localhost:15672` → pestaña **Queues and Streams** → seleccionar la cola → sección **Get messages** → indicar la cantidad de mensajes a obtener y presionar **Get Message(s)**.

---

## 14. Evidencias de ejecución
Evidencia adjunta en carpeta docs

| # | Evidencia requerida |
|---|---------------------|
| 1 | Contenedor RabbitMQ ejecutándose (`docker ps`) |
| 2 | RabbitMQ Management UI — pantalla principal |
| 3 | Exchange `campus.exchange` creado |
| 4 | Listado de las seis colas creadas |
| 5 | Mensaje publicado en `campus.requests.in` |
| 6 | Mensaje transformado en `campus.admissions.queue` |
| 7 | Mensaje transformado en `campus.payments.queue` |
| 8 | Mensaje transformado en `campus.support.queue` |
| 9 | Mensaje transformado en `campus.academic.queue` |
| 10 | Mensaje no reconocido (`LIBRARY`) en `campus.manual-review.queue` |
| 11 | Mensaje inválido (campos faltantes) en `campus.manual-review.queue` |
| 12 | Logs de Apache Camel mostrando el flujo completo |

---

## 15. Problemas encontrados y cómo los resolvieron

| Problema | Solución aplicada |
|----------|-----------------|
| *Imagen de RabbitMQ no funcionaba* | *Buscar otra imagen estable para levantar* |
| *Problemas de autorización para scripts* | *Dar permisos y cambiar credenciales de Rabbit porque usabamos unas diferentes* |
| *Archivos no compilaban por el pom.xml * | *pom.xml necesitaba cambiar una dependencia, actualizandola ya funcionó* |
## 16. Preguntas de reflexión

### Pregunta 1
**¿Qué problema resuelve el patrón Message Translator en este taller?**

En este taller, el problema concreto es que los mensajes que llegan a la cola `campus.requests.in` provienen de múltiples canales externos (formulario web, app móvil, plataforma administrativa) y todos usan un formato propio definido por el canal, no por la institución. Ese formato externo tiene dos características que lo hacen incompatible con los sistemas internos:

1. **Nomenclatura en `snake_case` plana:** los campos se llaman `request_id`, `student_name`, `student_document`, `request_type`, `created_at`. Los sistemas internos de la institución usan `camelCase`.
2. **Estructura plana sin jerarquía:** los datos del estudiante (`student_name`, `student_document`) viven al mismo nivel que el resto del mensaje, cuando internamente se espera un objeto anidado `student` con `fullName` y `document`.

Sin el patrón Message Translator, cada sistema destino (`campus.admissions.queue`, `campus.payments.queue`, etc.) tendría que conocer y parsear el formato externo de cada canal productor. Si mañana el formulario web decide cambiar `student_name` por `nombre_completo`, todos los sistemas consumidores deberían actualizarse simultáneamente.

El componente `CanonicalRequestTranslator.java` resuelve este problema en un único punto del pipeline: intercepta el mensaje crudo, valida que todos los campos obligatorios estén presentes y no vacíos, y produce un nuevo objeto JSON en el formato canónico interno. A partir de ese momento, el resto de la ruta Camel y todos los sistemas consumidores trabajan exclusivamente con el formato canónico, completamente aislados de los detalles del formato externo.

Como efecto adicional importante, la validación integrada en el translator detecta mensajes incompletos (como el `REQ-1006` que llega sin `student_name`, `student_document` ni `request_type`) y los marca con `"status": "INVALID"` antes de que el router intente procesarlos, evitando que mensajes malformados lleguen a las colas de negocio.

---

### Pregunta 2
**¿Qué problema resuelve el patrón Content-Based Router en este taller?**

El problema central es que una única cola de entrada (`campus.requests.in`) recibe solicitudes de naturaleza completamente distinta: una solicitud de admisión, un pago de matrícula, un ticket de soporte técnico y una consulta académica no deben ser procesadas por el mismo sistema. Sin embargo, todos los productores (web, móvil, admin) publican al mismo punto de entrada porque no conocen —ni deben conocer— qué sistema interno se encargará de cada tipo.

Sin el Content-Based Router existirían dos alternativas, ambas problemáticas:

- **Opción A — Un único consumidor para todo:** un solo sistema leería todos los mensajes de `campus.requests.in` y decidiría internamente cómo procesarlos, acumulando lógica de múltiples dominios de negocio en un solo componente.
- **Opción B — Múltiples colas de entrada especializadas:** cada canal productor publicaría directamente en la cola correcta (`campus.admissions.queue`, `campus.payments.queue`, etc.), lo que obligaría a cada productor a conocer la topología completa de colas y a reimplementar la lógica de decisión.

El patrón Content-Based Router resuelve esto de forma elegante: en `CampusRequestRoute.java`, el bloque `.choice()/.when()/.otherwise()` lee el valor del campo `type` del mensaje canónico y toma la decisión de enrutamiento de forma centralizada. El resultado es que:

- Los cuatro productores existentes (y cualquier futuro productor) siempre publican en `campus.requests.in` sin conocer nada más sobre la arquitectura interna.
- El sistema de admisiones solo recibe mensajes `ADMISSION`, el de pagos solo recibe `PAYMENT`, y así sucesivamente.
- Los mensajes con tipos no contemplados (`LIBRARY`, `SCHOLARSHIP`) y los mensajes inválidos son capturados por el bloque `.otherwise()` y enviados a `campus.manual-review.queue` para revisión humana, sin que ningún sistema de negocio reciba mensajes que no le corresponden.

En síntesis, el Content-Based Router actúa como el despachador inteligente del sistema: es el único componente que necesita conocer la topología de colas y las reglas de enrutamiento, manteniendo ese conocimiento centralizado y aislado del resto.

---

### Pregunta 3
**¿Por qué primero se transforma el mensaje y luego se enruta?**

Este orden no es arbitrario; responde a una decisión de diseño con consecuencias técnicas y arquitectónicas concretas. Analicemos qué ocurriría en el escenario inverso para entender por qué el orden correcto es transformar primero.

**Si se enrutara primero y luego se transformara:**

El Content-Based Router necesitaría leer el campo de decisión directamente del mensaje externo crudo, es decir, `request_type` en `snake_case`. Esto significa que el router estaría acoplado al formato externo. Si un nuevo canal productor llamara a ese campo `tipo_solicitud` o `requestType`, el router fallaría o habría que duplicar la lógica de lectura del campo para cada variante de formato. Peor aún, cada cola destino recibiría los mensajes en formato externo crudo, y cada sistema consumidor (admisiones, pagos, soporte) tendría que encargarse de transformar el formato por su cuenta, duplicando la lógica de transformación en N sistemas distintos.

**Por qué transformar primero es correcto:**

En `CampusRequestRoute.java`, la secuencia de la ruta es:

```
.process(canonicalRequestTranslator)   ← PRIMERO: transformar
.choice()                              ← LUEGO: enrutar
  .when(exchangeProperty("requestType")...)
```

Esto garantiza cuatro propiedades fundamentales:

1. **El router trabaja con el formato estable y controlado internamente:** lee la propiedad `requestType` (ya almacenada en el exchange por el translator), no el campo externo `request_type`. Cualquier cambio en el nombre del campo externo solo afecta al translator, nunca al router.

2. **Todos los sistemas consumidores reciben mensajes ya transformados:** cuando `campus.admissions.queue` entrega un mensaje al sistema de admisiones, ese mensaje ya está en formato canónico. El sistema de admisiones no necesita saber nada sobre el formato original del formulario web.

3. **La validación ocurre antes del enrutamiento:** el translator detecta mensajes inválidos (campos faltantes) y les asigna `requestType = INVALID` antes de que el router los evalúe. Así, el bloque `.otherwise()` captura tanto los tipos no reconocidos como los mensajes inválidos en un solo punto de control, sin necesidad de añadir validaciones en cada rama del router.

4. **El pipeline tiene una única dirección de dependencia:** el translator conoce el formato externo pero no sabe nada de las colas; el router conoce las colas pero no sabe nada del formato externo. Esta separación de responsabilidades es posible precisamente porque la transformación ocurre antes del enrutamiento.

---

### Pregunta 4
**¿Qué pasaría si cada productor tuviera que conocer todas las colas destino?**

Este escenario describe exactamente el antipatrón que el Content-Based Router está diseñado para evitar. Analizarlo en el contexto de este taller permite apreciar el valor real del patrón.

En este taller existen al menos cuatro canales productores: formulario web, aplicación móvil, plataforma administrativa e integraciones externas futuras. Si cada uno tuviera que conocer las colas destino y decidir a cuál publicar, ocurriría lo siguiente:

**Problema 1 — Acoplamiento estructural a la infraestructura:**  
Cada productor tendría que importar y gestionar la lógica para conectarse y publicar en `campus.admissions.queue`, `campus.payments.queue`, `campus.support.queue`, `campus.academic.queue` y `campus.manual-review.queue`. El código del formulario web, cuya responsabilidad es capturar datos del usuario, quedaría contaminado con detalles de la topología de mensajería.

**Problema 2 — Duplicación masiva de lógica:**  
La lógica de decisión `"si request_type == ADMISSION, publicar en campus.admissions.queue"` estaría replicada en el formulario web, en la app móvil, en la plataforma administrativa y en cada integración externa. Con cuatro productores y cinco reglas de enrutamiento, habría 4 × 5 = 20 piezas de lógica distribuidas que deben mantenerse sincronizadas.

**Problema 3 — Fragilidad ante cambios:**  
Si el equipo de operaciones decide renombrar `campus.payments.queue` a `campus.financial.queue`, habría que localizar, modificar, probar y redesplegar los cuatro productores simultáneamente. Un error en cualquiera de ellos podría causar que mensajes de pago sean enviados a la cola incorrecta o se pierdan.

**Problema 4 — Incorporación de nuevos productores costosa:**  
Cuando llegue la integración con un sistema externo nuevo, ese sistema tiene que recibir y mantener la lógica de enrutamiento completa desde el primer día, incluyendo el conocimiento de todas las colas existentes y sus criterios de selección.

**Problema 5 — Imposibilidad de auditoría centralizada:**  
Si las decisiones de enrutamiento ocurren de forma distribuida en cada productor, resulta muy difícil responder preguntas como "¿cuántos mensajes de tipo ADMISSION se recibieron hoy?" sin agregar logs de múltiples sistemas diferentes.

En contraste, con el Content-Based Router en `CampusRequestRoute.java`, todos estos problemas desaparecen: los productores publican a `campus.requests.in` y delegan la responsabilidad de enrutamiento al componente centralizado.

---

### Pregunta 5
**¿Qué ventaja tiene usar un modelo canónico interno?**

El modelo canónico es probablemente la decisión de diseño con mayor impacto a largo plazo en toda la arquitectura de integración. Sus ventajas se hacen más evidentes a medida que el número de sistemas participantes crece.

**Ventaja 1 — Reducción exponencial de conversiones necesarias:**  
Sin un modelo canónico, con N fuentes de datos y M sistemas consumidores, se necesitarían hasta N × M conversiones de formato distintas (cada fuente habla directamente con cada destino en su propio dialecto). Con el modelo canónico, solo se necesitan N traducciones de entrada (cada fuente al canónico) y M traducciones de salida (del canónico a cada destino). En este taller, con potencialmente 4 productores y 5 sistemas consumidores, se pasaría de hasta 20 conversiones a solo 9.

**Ventaja 2 — Aislamiento ante cambios externos:**  
Si el formulario web cambia su campo `student_name` a `nombre`, solo hay que actualizar `CanonicalRequestTranslator.java`. Los sistemas de admisiones, pagos, soporte y académico siguen recibiendo exactamente el mismo formato canónico con `student.fullName`, sin enterarse de que algo cambió externamente. Esta propiedad es especialmente valiosa cuando los productores son sistemas de terceros sobre los que no se tiene control.

**Ventaja 3 — Contrato claro y estable para los consumidores:**  
Todos los sistemas que consumen de las colas destino acuerdan un único contrato: el modelo canónico. El sistema de admisiones sabe que siempre recibirá `requestId`, `student.fullName`, `student.document`, `type`, `sourceChannel` y `createdAt`. No existe ambigüedad, no hay que negociar formatos caso por caso, y la documentación del modelo canónico es suficiente para que cualquier equipo integre un nuevo consumidor.

**Ventaja 4 — Validación y enriquecimiento centralizados:**  
Como el modelo canónico pasa por el `CanonicalRequestTranslator`, ese es el lugar natural para aplicar validaciones de negocio (campos obligatorios), normalizaciones (convertir fechas a ISO 8601, unificar mayúsculas/minúsculas) o enriquecimiento (agregar un `timestamp` de procesamiento). Estas reglas se aplican una sola vez para todos los productores.

**Ventaja 5 — Facilita la evolución del sistema:**  
Si en el futuro se decide añadir un campo al modelo canónico (por ejemplo, `priority` para priorización de solicitudes), ese campo estará disponible para todos los consumidores de forma inmediata. Sin el modelo canónico, habría que negociar y propagar ese cambio productor a productor y consumidor a consumidor de forma coordinada.

---

### Pregunta 6
**¿Qué limitaciones tiene esta solución?**

La solución actual presenta varias limitaciones identificables:

- **Escalabilidad limitada del router:** cada nuevo tipo de solicitud requiere modificar manualmente el código de `CampusRequestRoute.java` y redesplegar la aplicación. No existe un mecanismo dinámico de registro de rutas sin intervención del desarrollador.
- **Sin política de reintentos ante fallos:** si RabbitMQ no está disponible momentáneamente o una cola destino presenta problemas, no hay una política de reintento configurada. Un mensaje podría perderse sin dejar rastro.
- **Sin Dead Letter Queue real:** aunque `campus.manual-review.queue` actúa como destino para mensajes inválidos, es una cola de aplicación gestionada manualmente, no una DLQ técnica de RabbitMQ con política de expiración, alertas automáticas o encabezados de diagnóstico enriquecidos.
- **Transformación acoplada al esquema de entrada:** si el formato externo cambia (un campo renombrado, un nuevo campo obligatorio), el `CanonicalRequestTranslator` debe actualizarse manualmente.
- **Sin autenticación segura:** la configuración usa las credenciales por defecto de RabbitMQ (`guest/guest`), lo que no es aceptable en un entorno productivo.
- **Sin monitoreo ni métricas:** no existe integración con herramientas de observabilidad (Prometheus, Grafana, Camel Management Console) para medir throughput, latencia o tasa de errores en tiempo real.

---

### Pregunta 7
**¿Cómo se podría mejorar el manejo de errores?**

Existen múltiples estrategias complementarias para robustecer la solución:

- **Dead Letter Exchange (DLX) nativo de RabbitMQ:** configurar en el broker un exchange de errores de modo que los mensajes rechazados (nacked) sean redirigidos automáticamente a una DLQ con sus cabeceras de diagnóstico originales, sin intervención de la aplicación.
- **Error Handler de Apache Camel:** implementar un bloque `onException()` en la ruta para capturar excepciones específicas (`JsonProcessingException`, `ConnectException`) y aplicar políticas diferenciadas: reintentar N veces con backoff exponencial para errores transitorios, o enviar directamente a revisión manual para errores de formato irrecuperables.
- **Política de reentrega (`redeliveryPolicy`):** usar la configuración nativa de Camel para definir cuántas veces se reintenta el procesamiento de un mensaje fallido antes de moverlo a la cola de errores.
- **Logging estructurado:** registrar los errores con contexto enriquecido (`requestId`, tipo de error, timestamp, payload original) en un sistema centralizado como ELK Stack o AWS CloudWatch para facilitar la auditoría y el diagnóstico.
- **Alertas operacionales:** configurar umbrales de profundidad de cola en RabbitMQ para que si `campus.manual-review.queue` acumula más de N mensajes, se genere una notificación automática indicando un problema sistemático en el pipeline.

---

### Pregunta 8
**¿Qué cambios serían necesarios para soportar el nuevo tipo de solicitud SCHOLARSHIP?**

Para incorporar formalmente el tipo `SCHOLARSHIP` se requieren los siguientes pasos:

1. **Crear una nueva cola en RabbitMQ:** `campus.scholarship.queue` con `durable: true`.
2. **Crear el binding:** enlazar `campus.scholarship.queue` al exchange `campus.exchange` usando `campus.scholarship.queue` como routing key.
3. **Actualizar el Content-Based Router:** agregar una nueva condición `.when()` en `CampusRequestRoute.java` antes del bloque `.otherwise()`:
   ```java
   .when(exchangeProperty("requestType").isEqualTo("SCHOLARSHIP"))
     .log("Enrutando solicitud de beca")
     .to("spring-rabbitmq:campus.exchange?routingKey=campus.scholarship.queue")
   ```
4. **Actualizar el script `setup-rabbitmq.sh`:** añadir `campus.scholarship.queue` al array `QUEUES` para que el script la cree y la registre automáticamente.
5. **Agregar pruebas:** incluir un mensaje con `"request_type": "SCHOLARSHIP"` en `publish-messages.sh` y verificar en RabbitMQ Management UI que llega a la cola correcta.
6. **Actualizar la documentación:** reflejar el nuevo tipo en la tabla de reglas de enrutamiento del README.

Este proceso ilustra la limitación señalada en la pregunta anterior: la adición de nuevos tipos requiere modificar y redesplegar la aplicación. Una mejora futura sería externalizar las reglas de enrutamiento a un archivo de configuración o base de datos, eliminando la necesidad de tocar el código Java.

---

### Pregunta 9
**¿Qué riesgos tendría colocar toda la lógica de decisión en el productor del mensaje?**

Colocar la lógica de enrutamiento en el productor generaría los siguientes riesgos críticos:

- **Alto acoplamiento estructural:** el productor necesitaría conocer el nombre, propósito y estado de cada cola destino. Cualquier cambio en la topología de colas (renombrar, agregar, eliminar) obligaría a modificar y redesplegar todos los productores involucrados.
- **Duplicación de lógica:** si existen múltiples productores (formulario web, app móvil, plataforma administrativa), cada uno debería replicar la misma lógica de decisión. Con el tiempo, la inconsistencia entre versiones sería inevitable.
- **Fragilidad ante cambios:** agregar un nuevo tipo de solicitud requeriría actualizar y redesplegar simultáneamente todos los productores, aumentando el riesgo operacional y la probabilidad de errores de sincronización.
- **Violación del principio de responsabilidad única (SRP):** el productor mezclaría su responsabilidad principal (capturar y emitir la solicitud) con responsabilidades de infraestructura (conocer y gestionar la topología de mensajería).
- **Dificultad para pruebas unitarias:** el productor sería más difícil de probar de forma aislada porque su comportamiento dependería del estado de la infraestructura de mensajería, haciendo los tests más complejos y frágiles.

El patrón Content-Based Router existe precisamente para evitar este escenario, centralizando las decisiones de enrutamiento en un componente especializado y dejando a los productores libres de esa responsabilidad.

---

### Pregunta 10
**¿Cómo se relaciona este taller con una arquitectura orientada a eventos?**

Este taller implementa los fundamentos de una **arquitectura orientada a eventos (EDA — Event-Driven Architecture)** y puede verse como una introducción práctica a sus principios:

- **El mensaje como evento:** cada solicitud que llega a `campus.requests.in` es en esencia un evento de dominio: un estudiante realizó una solicitud. El sistema reacciona a este evento en lugar de ser invocado directamente mediante una llamada síncrona (REST, RPC).
- **Desacoplamiento temporal:** el productor publica el evento y continúa su operación sin esperar respuesta. El consumidor (Apache Camel y los sistemas destino) lo procesa de forma asíncrona e independiente. Esto permite que ambos operen a velocidades distintas sin bloquearse mutuamente.
- **Escalabilidad independiente:** en una EDA completa, cada consumidor de cola podría escalar horizontalmente de manera autónoma según la carga específica de su tipo de solicitud, sin afectar al resto del sistema.
- **Extensibilidad natural:** nuevos suscriptores pueden incorporarse al sistema simplemente escuchando una cola existente, sin modificar a los productores ni al router. Esta es la base de los sistemas event-driven reales.
- **Trazabilidad como efecto secundario:** el flujo de mensajes a través de las colas deja una traza natural del estado de cada solicitud, similar a un event log inmutable, lo que facilita la auditoría y el debugging post-mortem.

La diferencia principal entre este taller y una EDA madura es que aquí los mensajes son comandos (solicitudes de acción que esperan ser procesadas), no eventos de dominio puros (notificaciones de hechos consumados). En una EDA completa, el evento podría llamarse `AdmissionRequested` y múltiples consumidores independientes reaccionarían a él de forma paralela: el sistema de admisiones, el de notificaciones, el de reportes, etc.

---

## 17. Reflexión técnica final

Este taller demuestra cómo dos patrones de integración bien establecidos —**Message Translator** y **Content-Based Router**— permiten construir una arquitectura de mensajería robusta sin acoplar directamente a los sistemas participantes.

La clave está en el orden del pipeline: primero transformar, luego enrutar. Si se enrutara antes de transformar, cada cola destino recibiría mensajes en diferentes formatos externos, obligando a cada sistema consumidor a conocer y manejar todos los formatos posibles de entrada. Al centralizar la transformación al inicio del flujo, todos los sistemas internos hablan el mismo idioma canónico.

El modelo canónico actúa como el contrato de integración. Una vez definido y respetado, incorporar un nuevo canal de entrada o un nuevo sistema consumidor se convierte en un ejercicio de traducción local, sin impacto en el resto de la arquitectura. Esta propiedad —la capacidad de crecer sin modificar lo existente— es una de las características más valiosas de las arquitecturas de integración bien diseñadas y constituye un puente directo hacia los principios de las arquitecturas orientadas a eventos y los microservicios modernos.

---

*Taller Semana 11 — Integración de Sistemas (ISWZ3104) · Universidad de las Américas, Ecuador.*
