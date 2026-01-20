package top.ligoudaner.classpoints.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import top.ligoudaner.classpoints.databinding.ItemStudentBinding;
import top.ligoudaner.classpoints.model.Student;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {

    private List<Student> students = new ArrayList<>();
    private OnStudentClickListener listener;

    public interface OnStudentClickListener {
        void onAddPointClick(Student student);
        void onStudentLongClick(Student student);
    }

    public void setOnStudentClickListener(OnStudentClickListener listener) {
        this.listener = listener;
    }

    public void setStudents(List<Student> students) {
        this.students = students;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStudentBinding binding = ItemStudentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Student student = students.get(position);
        holder.binding.tvStudentId.setText(String.valueOf(student.id));
        holder.binding.tvStudentName.setText(student.name);

        double weeklyTotal = student.currentWeeklyPoints;
        double cumulativeTotal = student.totalPoints + student.currentWeeklyPoints;

        holder.binding.tvWeeklyTotal.setText("本周总分: " + weeklyTotal);
        holder.binding.tvCumulativeTotal.setText("累积总分: " + cumulativeTotal);

        holder.binding.btnAddPoint.setOnClickListener(v -> {
            if (listener != null) listener.onAddPointClick(student);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onStudentLongClick(student);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemStudentBinding binding;
        ViewHolder(ItemStudentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
