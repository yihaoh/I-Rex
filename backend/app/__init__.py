from flask import Flask
from .config import Config
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from flask_cors import CORS
from dotenv import load_dotenv
import logging

logging.basicConfig(filename='error.log', level=logging.ERROR, format=f'%(asctime)s %(levelname)s %(name)s %(threadName)s : %(message)s')


def create_app():
    app = Flask(__name__)

    from .trace import bp as trace_bp
    app.register_blueprint(trace_bp)

    return app
