# PukysCore

**PukysCore** es un mod de utilidades y gestión nativo para **Forge**, diseñado específicamente para optimizar y unificar las mecánicas del servidor de **PukysCraft**.

---

## 🚀 Características Principales y Módulos

El mod está dividido en módulos eficientes para garantizar el mejor rendimiento del servidor, gestionando de forma unificada desde la autenticación hasta la protección de terrenos.

### 🔒 Módulo de Autenticación (Auth)
Sistema de registro y login (Premium y No Premium) diseñado para evitar exploits de movimiento y desincronización de inventarios.
- **Seguridad Estricta:** Manejo de sesiones, encriptación, bloqueos por IP y tiempos de espera. Bloqueo total de interacciones, comandos, daño y movimiento antes de iniciar sesión exitosamente.
- **Comandos:** `/register`, `/login`

### 🛡️ Módulo de Protección (Protection)
Sistema de protección de terrenos mediante "Bloques de Protección", configurable al 100% de forma nativa.
- **Alto Rendimiento:** Detección de miembros en tiempo real usando consultas ligeras para evitar saturar las llamadas de red.
- **Gestión Total:** Validación de áreas y administración de miembros.
- **Comandos:** `/pc add`, `/pc remove`, `/pc info`, `/pc give` (Solo Admin)

### 📍 Módulo de Teletransporte y Funciones
Gestión completa de viajes, puntos de aparición e historial de muertes, persistido de forma asíncrona en formato `.json`.
- **Comandos de Usuario:** `/sethome`, `/home`, `/delhome`, `/homelist`, `/tpa`, `/tphere`, `/back` (con soporte integral para muertes).
- **Comandos de Admin:** `/setwarp`, `/delwarp`, `/tpall`

### ⚙️ Configuración y Permisos
- **Configuración Nativa (TOML):** Generación automática del archivo `PukysCore/config.toml`. Soporta recarga en vivo (*Hot-Reload*) permitiendo modificar tiempos de expiración de sesiones, radios de los bloques de protección, materiales y más, sin reiniciar el servidor.
- **Integración de Permisos:** Soporte nativo para nodos de permisos y límites (homes, protecciones) a través de Forge API.

---

## 🛠️ Requisitos de Desarrollo
Para contribuir a este proyecto, necesitarás las siguientes herramientas instaladas en tu equipo:
- **Java Development Kit (JDK):** Versión 17 (o superior, ajustando la compatibilidad correspondientemente).
- **IDE Recomendado:** IntelliJ IDEA (Community o Ultimate).
- **Git:** Para el control de versiones.

---

## 💻 Instalación y Configuración del Entorno
Si deseas compilar el código o preparar el entorno para una contribución, sigue estos pasos:

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/LaraKnife/PukysCore.git
   ```

2. **Abrir en IntelliJ IDEA:**
   - Abre IntelliJ y ve a `File` -> `Open`.
   - Busca la carpeta `PukysCore` que acabas de clonar y selecciona el archivo `build.gradle`.
   - *Nota:* IntelliJ detectará automáticamente que es un proyecto Gradle y comenzará a descargar las dependencias de Forge. Esto puede tardar unos minutos.

3. **Generar las configuraciones de ejecución (Run Configurations):**
   - Abre la terminal integrada en IntelliJ y ejecuta el siguiente comando para generar las configuraciones de cliente y servidor:
   ```bash
   # En Linux / macOS
   ./gradlew genIntellijRuns
   
   # En Windows
   gradlew genIntellijRuns
   ```

4. **Ejecutar el entorno de prueba:**
   - En la parte superior derecha de IntelliJ, ahora verás las configuraciones de ejecución `runClient` y `runServer`.
   - Selecciona `runServer` y presiona el botón de **Debug** (el ícono del bicho) para probar el mod localmente.

---

## 🤝 Cómo Contribuir (Pull Requests)

¡Las contribuciones son bienvenidas! Sigue este flujo de trabajo para enviar tus mejoras al código de PukysCore:

1. Haz un **Fork** de este repositorio.
2. Crea una rama para tu característica o corrección: 
   ```bash
   git checkout -b feature/NuevaCaracteristica
   ```
3. Haz tus cambios y asegúrate de incluir un commit claro y descriptivo.
4. Sube los cambios a tu rama en GitHub: 
   ```bash
   git push origin feature/NuevaCaracteristica
   ```
5. Abre un **Pull Request** en este repositorio explicando detalladamente tus modificaciones, el problema que resuelven o la mejora que implementan.
