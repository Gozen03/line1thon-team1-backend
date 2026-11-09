from django.urls import path

from ai import views

urlpatterns = [
    path("health", views.health),
    path("ai/v1/questions", views.questions),
    path("ai/v1/feedback", views.feedback),
    path("ai/v1/review-questions", views.review_questions),
    path("ai/v1/review-feedback", views.review_feedback),
]
