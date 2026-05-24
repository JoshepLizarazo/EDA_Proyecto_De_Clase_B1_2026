# Simulador de Propagación de Epidemia — Contexto del Proyecto

## Información académica

| Campo | Detalle |
|---|---|
| **Universidad** | Universidad Industrial de Santander (UIS) |
| **Facultad** | Ingeniería de Sistemas e Informática |
| **Asignatura** | Estructuras de Datos y Análisis de Algoritmos — Código 22955 |
| **Grupo** | B1-C1 |
| **Docente** | Laura Viviana Galvis Carreño |
| **Peso en la nota** | 20% del curso |
| **Entregables** | Informe escrito (30%) + Código Java (30%) + Exposición 10 min (40%) |

---

## Origen del proyecto

Este proyecto evoluciona directamente de un trabajo previo desarrollado en la asignatura de Matemáticas Discretas, donde se simuló la propagación de una epidemia sobre distintos tipos de redes sociales usando Python, NetworkX y matplotlib. En esa versión, los nodos del grafo eran entidades simples sin atributos propios.

La versión actual, desarrollada para Estructuras de Datos, sube la dificultad en tres dimensiones:

1. **Nodos con peso real** — cada persona tiene atributos demográficos (edad, estrato, ocupación) que influyen directamente en la probabilidad de contagio de sus aristas.
2. **Implementación en Java** — se aplica arquitectura por capas, estructuras de datos propias (`HashMap`, `PriorityQueue`, listas de adyacencia) y buenas prácticas de diseño.
3. **Estrategia híbrida propia** — se introduce un algoritmo de vacunación original que combina posición estructural del nodo en la red con su perfil de riesgo individual.

---

## Descripción del problema

Las epidemias se propagan de manera distinta según la estructura social de una comunidad y las características individuales de sus integrantes. El objetivo es modelar cómo una enfermedad se expande a través de una red social representada como un grafo ponderado y dirigido, donde cada persona tiene atributos demográficos propios.

A partir de este modelo, se evalúa qué estrategia de vacunación —con recursos limitados al **20% de la población**— logra contener el brote de forma más eficaz, comparando cinco enfoques distintos.

Para que la comparación sea **justa**, el brote arranca con un **15% de infectados iniciales** que son los mismos para todas las estrategias (se fijan antes de vacunar), y la vacunación del 20% se aplica sobre los susceptibles restantes. Además del modo individual y del comparativo (las estrategias sobre una misma red), un **modo por lotes** corre N grafos distintos por estrategia y promedia los resultados para un veredicto estadísticamente más robusto.

---

## Objetivos

### Objetivo general
Diseñar e implementar en Java un simulador de propagación epidémica sobre una red social generada con patrones demográficos colombianos reales, y determinar computacionalmente cuál estrategia de vacunación minimiza el impacto del brote.

### Objetivos específicos
- Implementar un grafo ponderado y dirigido con listas de adyacencia usando `HashMap<Persona, List<Contacto>>`.
- Modelar la propagación mediante el modelo SIRV (Susceptible → Infectado → Recuperado → Vacunado).
- Generar automáticamente una población con distribución demográfica colombiana realista (edades, estratos socioeconómicos, ocupaciones).
- Implementar cinco estrategias de vacunación: aleatoria, por hubs, por betweenness, dentro de comunidades e híbrida (aporte propio del equipo).
- Incorporar un mecanismo de actualización dinámica de pesos en runtime mediante eventos automáticos por umbral de infectados.
- Visualizar la propagación en tiempo real usando GraphStream, con nodos coloreados por estado SIRV.
- Comparar estadísticamente las cinco estrategias y determinar la más efectiva.

---

## Características del grafo

| Propiedad | Valor |
|---|---|
| **Tipo** | Dirigido y ponderado |
| **Estructura interna** | `HashMap<Persona, List<Contacto>>` |
| **Nodo** | `Persona` — id, nombre, edad, estrato, ocupación, `EstadoSIRV` |
| **Arista** | `Contacto` — nodo origen, nodo destino, `probContagio` (peso) |
| **Peso de arista** | Probabilidad de contagio entre dos personas (0.0 – 1.0) |
| **Casos de prueba** | Dos grafos: red pequeña ~80 nodos y red grande ~300 nodos |

---

## Generación de la población (patrones colombianos)

La red social no se carga desde un CSV estático — se **genera algorítmicamente** con distribuciones basadas en datos reales de Colombia:

### Distribución de edades
Basada en la pirámide poblacional colombiana (DANE). Mayor concentración entre 15 y 35 años. A mayor edad, mayor peso de contagio en sus aristas (mayor vulnerabilidad).

### Distribución de estratos
- Estrato 1–2: 60% de la población
- Estrato 3: 30%
- Estrato 4–6: 10%

Estratos bajos generan más aristas (hacinamiento, transporte público masivo) con mayor peso de contagio.

### Ocupaciones y su impacto en la red
| Ocupación | Impacto en conectividad |
|---|---|
| Trabajador de salud | Alta exposición — muchas aristas, pesos altos |
| Estudiante | Muchas aristas dentro de clusters escolares |
| Trabajador informal / vendedor | Alto contacto aleatorio fuera del cluster |
| Empleado formal | Aristas moderadas, contexto laboral |
| Jubilado | Pocas aristas, menor exposición |

### Estructura de clusters
La red imita el comportamiento social colombiano:
- **Clusters familiares** de 4–7 personas con aristas muy fuertes entre sí.
- **Vecindarios** con conexiones moderadas entre familias del mismo barrio.
- **Hubs comunitarios** (mercado, iglesia, transporte público) que conectan múltiples clusters con aristas de peso variado.

---

## Mecanismo de actualización de pesos — Eventos por umbral

Durante la simulación, cuando el porcentaje de infectados supera ciertos umbrales predefinidos, el sistema dispara automáticamente un evento que modifica los pesos de todas las aristas del grafo, simulando intervenciones de salud pública:

| Evento | Umbral | Factor multiplicador | Significado |
|---|---|---|---|
| `ALERTA_LEVE` | 30% infectados | × 0.80 | Distanciamiento social voluntario |
| `CUARENTENA` | 50% infectados | × 0.50 | Restricción de movilidad obligatoria |
| `LOCKDOWN` | 70% infectados | × 0.20 | Confinamiento total |

Cada evento solo se dispara una vez. El `GestorEventos` verifica en cada turno si se cruzó un umbral aún no activado.

---

## Visualización con GraphStream

La propagación se muestra en tiempo real en una ventana gráfica. Los nodos cambian de color según su estado SIRV:

| Estado | Color |
|---|---|
| Susceptible | Azul `#3498db` |
| Infectado | Rojo `#e74c3c` |
| Recuperado | Verde `#2ecc71` |
| Vacunado | Amarillo/naranja `#f39c12` |

El grosor de cada arista refleja su `probContagio`. El tamaño del nodo refleja su grado de conexiones. Al finalizar la simulación se muestra un panel comparativo con las curvas SIRV por estrategia de vacunación.

---

## Resultado esperado

Al finalizar cada simulación se presenta:
- Curva de infectados por turno para cada estrategia.
- Pico máximo de infectados por estrategia.
- Duración total del brote (en turnos).
- Total de recuperados (infectados que pasaron por el sistema).
- Estrategia ganadora con justificación cuantitativa.

En el **modo por lotes**, además, se presenta una comparación **promedio** (métricas promediadas sobre los N grafos de cada estrategia) y **acumulada** (en cuántas corridas cada estrategia obtuvo el mejor score compuesto), con su estrategia ganadora.

Ambos tipos de informe PDF (individual y por lotes) incluyen al final una página de **Glosario de métricas — Guía de interpretación**: una tabla que explica cada variable del modelo (S, I, R, V, pico, t-pico, duración, afectados, contención %, R0, score compuesto y victorias), qué mide y qué valores se consideran favorables desde el punto de vista de salud pública. El informe de lotes incluye además la variable "Victorias".
