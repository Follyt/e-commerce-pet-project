🛍️ E-Commerce Pet Project

    Этот проект представляет собой учебное e-commerce-приложение, разработанное с целью практики построения поисковой системы и обработки данных о товарах.



📁 Структура проекта

    e-commerce-pet-project/
    ├── data/
    │   ├── products.json           # Основной файл с данными о товарах
    │   └── products1.json          # Альтернативный/тестовый файл с данными о товарах
    │
    ├── src/main/application/
    │   ├── schemas/
    │   │   └── product.sd          # Схема товара для индексирования и поиска
    │   │
    │   ├── search/query-profiles/
    │   │   └── default.xml         # Профиль поиска по умолчанию
    │   │
    │   ├── hosts.xml               # Конфигурация хостов/нод
    │   └── services.xml            # Определение сервисов поиска
    │
    ├── .gitignore                  # Игнорируемые Git файлы
    ├── pom.xml                     # Maven-конфигурация проекта
    └── README.md                   # Описание проекта



⚙️ Основные технологии

    * Java 

    * Vespa Engine – для полнотекстового поиска и индексирования

    * JSON – для хранения исходных данных о товарах

    * Maven – для управления зависимостями и сборки



🚀 Как запустить проект

    1) Убедитесь, что у вас установлена Vespa.

    2) Склонируйте репозиторий:
    git clone https://github.com/Follyt/e-commerce-pet-project.git
    cd e-commerce-pet-project

    3) Разверните Vespa:
    vespa-deploy prepare src/main/application
    vespa-deploy activate
    
    4) Загрузите данные: 
    vespa-feed-client --target http://localhost:8080 --file data/products.json



🔍 Поиск

    Используйте эндпоинт Vespa для выполнения поисковых запросов:
    http://localhost:8080/search/?query=ваш_запрос



🧪 Тестовые данные

    Файлы products.json и products1.json содержат примеры товаров с различными атрибутами. Используются для загрузки и тестирования полнотекстового поиска.



📄 Схема (Schema)

    Файл product.sd определяет структуру документов в индексе: поля, типы данных, индексацию и т.п.



🗂️ Конфигурация поиска

    * default.xml – дефолтный профиль поиска

    * services.xml – конфигурация Vespa-сервисов

    * hosts.xml – описание хостов




🧑‍💻 Автор

    Разработано с целью обучения и практики в работе с Vespa и системами полнотекстового поиска.
    Автор: Follyt

